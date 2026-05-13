package com.example.a20260310.viewmodel

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a20260310.data.model.Meeting
import com.example.a20260310.data.model.MeetingDraft
import com.example.a20260310.data.model.MeetingStatus
import com.example.a20260310.data.model.MinutesUiMapper
import com.example.a20260310.data.model.MinutesUiModel
import com.example.a20260310.data.remote.ApiErrorParser
import com.example.a20260310.data.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class SelectedSourceFile(
    val id: String = UUID.randomUUID().toString(),
    val type: Type,
    val displayName: String,
    val localPath: String,
    /** 녹음 세그먼트 m4a 경로(순서). 비어 있으면 [localPath]만 사용한다. */
    val segmentLocalPaths: List<String> = emptyList(),
) {
    enum class Type { AUDIO_RECORD, AUDIO_UPLOAD, IMAGE, DOCUMENT }
}

enum class SummaryPanelPhase {
    RUNNING,
    COMPLETED,
}

/**
 * 완료 결과 구분 (서버 꺼짐·타임아웃 등 테스트 폴백 vs 실제 API 성공).
 */
enum class SummaryCompletionMode {
    NONE,
    REAL_SUCCESS,
    /** 서버 미기동·연결/타임아웃 등으로 catch 더미-only 완료 — REAL_SUCCESS 아님 */
    TEST_NETWORK_FALLBACK,
}

data class SummaryProgressState(
    val meetingTitle: String,
    val progressPercent: Int,
    val etaSecondsRemaining: Long?,
    val isRunning: Boolean,
    val isComplete: Boolean,
    val phase: SummaryPanelPhase,
    val errorMessage: String?,
    val summarySucceeded: Boolean,
    /** RUNNING 뒤에 대기 중인 작업 수 (프로세스 메모리 큐만, 앱 재실행 시 복원 없음) */
    val waitingCount: Int = 0,
    val completionMode: SummaryCompletionMode = SummaryCompletionMode.NONE,
) {
    companion object {
        fun idle(): SummaryProgressState =
            SummaryProgressState(
                meetingTitle = "",
                progressPercent = 0,
                etaSecondsRemaining = null,
                isRunning = false,
                isComplete = false,
                phase = SummaryPanelPhase.RUNNING,
                errorMessage = null,
                summarySucceeded = true,
                waitingCount = 0,
                completionMode = SummaryCompletionMode.NONE,
            )
    }
}

/** 프로세스 메모리에만 유지되는 요약 작업 단위 */
private data class QueuedSummaryJob(
    val requestId: String,
    val draft: MeetingDraft,
    val files: List<SelectedSourceFile>,
    val meetingTitle: String,
    val estimateMs: Long,
)

class MeetingSessionViewModel(
    private val repository: MeetingRepository = MeetingRepository(),
) : ViewModel() {

    @Volatile
    private var draft: MeetingDraft = MeetingDraft()

    private val _meetingDraft = MutableLiveData(MeetingDraft())
    val meetingDraft: LiveData<MeetingDraft> = _meetingDraft

    private val _minutes = MutableLiveData<MinutesUiModel?>(null)
    val minutes: LiveData<MinutesUiModel?> = _minutes

    private val _isPipelineRunning = MutableLiveData(false)
    val isPipelineRunning: LiveData<Boolean> = _isPipelineRunning

    private val _pipelineError = MutableLiveData<String?>(null)
    val pipelineError: LiveData<String?> = _pipelineError

    private val _selectedFiles = MutableLiveData<List<SelectedSourceFile>>(emptyList())
    val selectedFiles: LiveData<List<SelectedSourceFile>> = _selectedFiles

    /** 폼 입력 기준 회의 엔티티. `serverFilePaths`는 업로드 응답 `file_path`만 누적한다. */
    private val _currentMeeting = MutableLiveData<Meeting?>(null)
    val currentMeeting: LiveData<Meeting?> = _currentMeeting

    private val _currentMeetingTitle = MutableLiveData("회의")
    val currentMeetingTitle: LiveData<String> = _currentMeetingTitle

    private val _currentBackendMeetingId = MutableLiveData<Int?>(null)
    val currentBackendMeetingId: LiveData<Int?> = _currentBackendMeetingId

    fun setCurrentMeetingTitle(title: String) {
        _currentMeetingTitle.value = title.trim().ifBlank { "회의" }
    }

    fun clearCurrentMeetingTitle() {
        _currentMeetingTitle.value = "회의"
        _currentBackendMeetingId.value = null
    }

    private val _summaryProgress = MutableLiveData(SummaryProgressState.idle())
    val summaryProgress: LiveData<SummaryProgressState> = _summaryProgress

    private val _summaryPanelExpanded = MutableLiveData(false)
    val summaryPanelExpanded: LiveData<Boolean> = _summaryPanelExpanded

    private val queueLock = Any()
    private val waitingQueue = ArrayDeque<QueuedSummaryJob>()
    private var runningJob: QueuedSummaryJob? = null
    private var queueProcessorRunning = false

    private var lastSummarizeUsedFallback: Boolean = false

    private fun uiMeetingTitle(): String = draft.title.trim().ifBlank { "회의" }

    fun setSummaryPanelExpanded(expanded: Boolean) {
        _summaryPanelExpanded.value = expanded
    }

    fun dismissSummaryProgressPanel() {
        _summaryPanelExpanded.value = false
        val busy =
            synchronized(queueLock) {
                queueProcessorRunning || runningJob != null || waitingQueue.isNotEmpty()
            }
        if (!busy) {
            _summaryProgress.value = SummaryProgressState.idle()
        }
    }

    fun setDraft(newDraft: MeetingDraft) {
        draft = newDraft
        _meetingDraft.value = newDraft
        _currentMeetingTitle.value = newDraft.title.trim().ifBlank { "회의" }

        _currentMeeting.value =
            Meeting(
                id = UUID.randomUUID().toString(),
                title = newDraft.title,
                date = newDraft.date,
                time = newDraft.time,
                participants = newDraft.participantList(),
                serverFilePaths = emptyList(),
                status = MeetingStatus.CREATED,
                summary = null,
            )
    }

    private fun patchCurrentMeeting(transform: (Meeting) -> Meeting) {
        val cur = _currentMeeting.value ?: return
        _currentMeeting.postValue(transform(cur))
    }

    /** 파이프라인 진행 중 초안만 있을 때 */
    private fun ensureMeetingFromDraft(snapshot: MeetingDraft): Meeting {
        val existing = _currentMeeting.value
        if (existing != null) return existing
        val created =
            Meeting(
                id = UUID.randomUUID().toString(),
                title = snapshot.title.ifBlank { "무제 회의" },
                date = snapshot.date,
                time = snapshot.time,
                participants = snapshot.participantList(),
                serverFilePaths = emptyList(),
                status = MeetingStatus.CREATED,
                summary = null,
            )
        _currentMeeting.postValue(created)
        return created
    }

    fun clearPipelineError() {
        _pipelineError.value = null
    }

    fun clearMinutes() {
        _minutes.value = null
    }

    fun addSelectedFile(file: SelectedSourceFile) {
        val current = _selectedFiles.value.orEmpty()
        _selectedFiles.value = current + file
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.orEmpty().filterNot { it.id == id }
    }

    /**
     * 새 회의 작성 플로우용 임시 첨부만 비운다.
     * 요약 큐·`minutes`·`summaryProgress`·`currentMeeting`(직전 결과)·파이프라인 오류 상태는 건드리지 않는다.
     */
    fun clearNewMeetingAttachmentSelection() {
        _selectedFiles.postValue(emptyList())
    }

    fun hasSelectedFilesForSummary(): Boolean = _selectedFiles.value.orEmpty().isNotEmpty()

    private fun waitingCountSnapshot(): Int = synchronized(queueLock) { waitingQueue.size }

    /**
     * 현재 선택된 회의·파일 스냅샷으로 요약 작업을 **메모리 큐**에 넣고,
     * 진행기가 한 번에 하나씩 순차 실행한다. (앱 프로세스 종료 시 큐 소멸)
     */
    fun startSummarizePipeline() {
        if (!hasSelectedFilesForSummary()) return

        val filesSnapshot = _selectedFiles.value.orEmpty().toList()
        val draftSnapshot = draft
        val title = uiMeetingTitle()
        val estimateMs = estimateDurationMs(filesSnapshot)

        val job =
            QueuedSummaryJob(
                requestId = UUID.randomUUID().toString(),
                draft = draftSnapshot,
                files = filesSnapshot,
                meetingTitle = title,
                estimateMs = estimateMs,
            )

        synchronized(queueLock) {
            waitingQueue.addLast(job)
        }

        bumpWaitingUiAfterEnqueue()
        ensureQueueProcessor()
    }

    private fun bumpWaitingUiAfterEnqueue() {
        val wc = waitingCountSnapshot()
        val cur = _summaryProgress.value
        if (cur == null) {
            if (wc > 0) emitWaitingOnlyUi()
            return
        }
        when {
            cur.isRunning && !cur.isComplete ->
                _summaryProgress.postValue(cur.copy(waitingCount = wc))
            !cur.isRunning && !cur.isComplete && wc > 0 ->
                emitWaitingOnlyUi()
        }
    }

    private fun emitWaitingOnlyUi() {
        val wc = waitingCountSnapshot()
        val nextTitle =
            synchronized(queueLock) { waitingQueue.firstOrNull()?.meetingTitle }.orEmpty()
        val etaSec =
            synchronized(queueLock) { waitingQueue.firstOrNull()?.estimateMs?.div(1000) }
        _summaryProgress.postValue(
            SummaryProgressState(
                meetingTitle = nextTitle,
                progressPercent = 0,
                etaSecondsRemaining = etaSec,
                isRunning = false,
                isComplete = false,
                phase = SummaryPanelPhase.RUNNING,
                errorMessage = null,
                summarySucceeded = true,
                waitingCount = wc,
                completionMode = SummaryCompletionMode.NONE,
            ),
        )
    }

    private fun ensureQueueProcessor() {
        synchronized(queueLock) {
            if (queueProcessorRunning) return
            queueProcessorRunning = true
        }
        viewModelScope.launch {
            try {
                while (true) {
                    val job =
                        synchronized(queueLock) {
                            if (waitingQueue.isEmpty()) {
                                queueProcessorRunning = false
                                null
                            } else {
                                waitingQueue.removeFirst()
                            }
                        } ?: break

                    synchronized(queueLock) {
                        runningJob = job
                    }
                    runQueuedJob(job)
                    synchronized(queueLock) {
                        runningJob = null
                    }
                }
            } finally {
                synchronized(queueLock) {
                    queueProcessorRunning = false
                }
            }
        }
    }

    private suspend fun runQueuedJob(job: QueuedSummaryJob) {
        _minutes.postValue(null)
        clearPipelineError()
        _summaryPanelExpanded.postValue(false)
        _isPipelineRunning.postValue(true)

        val estimateMs = job.estimateMs

        val ticker =
            viewModelScope.launch {
                val start = SystemClock.elapsedRealtime()
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val denom = max(estimateMs, 1L)
                    val pct = min(95, ((elapsed * 95L) / denom).toInt())
                    val etaSec = ((estimateMs - elapsed).coerceAtLeast(0L)) / 1000L
                    _summaryProgress.postValue(
                        SummaryProgressState(
                            meetingTitle = job.meetingTitle,
                            progressPercent = pct,
                            etaSecondsRemaining = etaSec,
                            isRunning = true,
                            isComplete = false,
                            phase = SummaryPanelPhase.RUNNING,
                            errorMessage = null,
                            summarySucceeded = true,
                            waitingCount = waitingCountSnapshot(),
                            completionMode = SummaryCompletionMode.NONE,
                        ),
                    )
                    delay(250)
                }
            }

        try {
            lastSummarizeUsedFallback = false
            performSummarizePipeline(job.draft, job.files)
            ticker.cancel()
        } finally {
            ticker.cancel()
            _isPipelineRunning.postValue(false)

            val mins = _minutes.value
            if (mins != null) {
                val mode =
                    if (lastSummarizeUsedFallback) {
                        SummaryCompletionMode.TEST_NETWORK_FALLBACK
                    } else {
                        SummaryCompletionMode.REAL_SUCCESS
                    }
                _summaryProgress.postValue(
                    SummaryProgressState(
                        meetingTitle = job.meetingTitle,
                        progressPercent = 100,
                        etaSecondsRemaining = 0L,
                        isRunning = false,
                        isComplete = true,
                        phase = SummaryPanelPhase.COMPLETED,
                        errorMessage = _pipelineError.value,
                        // 서버 미기동·타임아웃 폴백도 패널에서는 완료(체크)로 표시 — 구분은 completionMode
                        summarySucceeded = true,
                        waitingCount = waitingCountSnapshot(),
                        completionMode = mode,
                    ),
                )
            } else {
                _summaryProgress.postValue(
                    SummaryProgressState(
                        meetingTitle = job.meetingTitle,
                        progressPercent = 100,
                        etaSecondsRemaining = 0L,
                        isRunning = false,
                        isComplete = true,
                        phase = SummaryPanelPhase.COMPLETED,
                        errorMessage = _pipelineError.value ?: "요약 생성에 실패했습니다.",
                        summarySucceeded = false,
                        waitingCount = waitingCountSnapshot(),
                        completionMode = SummaryCompletionMode.NONE,
                    ),
                )
            }
        }
    }

    private fun estimateDurationMs(files: List<SelectedSourceFile>): Long {
        var totalAudioDurationMs = 0L
        var nonAudioCount = 0
        for (f in files) {
            when (f.type) {
                SelectedSourceFile.Type.AUDIO_RECORD,
                SelectedSourceFile.Type.AUDIO_UPLOAD -> {
                    val paths =
                        if (f.type == SelectedSourceFile.Type.AUDIO_RECORD) {
                            f.segmentLocalPaths.takeIf { it.isNotEmpty() } ?: listOf(f.localPath)
                        } else {
                            listOf(f.localPath)
                        }
                    for (p in paths) {
                        totalAudioDurationMs += mediaDurationMs(p)
                    }
                }
                SelectedSourceFile.Type.IMAGE,
                SelectedSourceFile.Type.DOCUMENT -> {
                    nonAudioCount += 1
                }
            }
        }
        val audioFactor = when {
            totalAudioDurationMs >= 3_600_000L -> 0.20 // 1시간 이상은 여유 있게 추정
            totalAudioDurationMs >= 1_800_000L -> 0.15 // 30분 이상
            else -> 0.10
        }
        val fromAudio = (totalAudioDurationMs * audioFactor).toLong() // 5분 오디오 ~= 30초
        val fromNonAudio = nonAudioCount * 3_000L
        val estimate = fromAudio + fromNonAudio
        return estimate.coerceAtLeast(10_000L)
    }

    private fun mediaDurationMs(path: String): Long {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return 0L
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            // 메타데이터를 읽지 못한 경우 최소 추정치로 보정
            15_000L
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun performSummarizePipeline(snapshot: MeetingDraft, selected: List<SelectedSourceFile>) {
        _pipelineError.value = null
        lastSummarizeUsedFallback = false
        ensureMeetingFromDraft(snapshot)
        patchCurrentMeeting { it.copy(status = MeetingStatus.PROCESSING) }
        try {
            val uploadedServerPaths = mutableListOf<String>()
            val created =
                withContext(Dispatchers.IO) {
                    repository.createMeeting(
                        title = snapshot.title.ifBlank { "무제 회의" },
                        meetingDate = snapshot.date.trim(),
                        meetingTime = snapshot.time.trim(),
                        attendees = snapshot.participantList(),
                        description = null,
                    )
                }
            _currentBackendMeetingId.postValue(created.id)
            var latestTranscriptText = ""
            withContext(Dispatchers.IO) {
                val audioFilesToUpload = mutableListOf<File>()
                val imageFilesToUpload = mutableListOf<File>()
                val tempGeneratedAudioFiles = mutableListOf<File>()

                selected.forEach { file ->
                    when (file.type) {
                        SelectedSourceFile.Type.AUDIO_RECORD,
                        SelectedSourceFile.Type.AUDIO_UPLOAD -> {
                            val local = File(file.localPath)
                            val audioFile =
                                when (file.type) {
                                    SelectedSourceFile.Type.AUDIO_RECORD -> {
                                        val segs = file.segmentLocalPaths
                                        val ordered =
                                            (segs.map { File(it) } + local)
                                                .filter { it.isFile && it.length() > 0L }
                                                .distinctBy { it.absolutePath }
                                        if (ordered.isEmpty()) null else mergeAudioSegmentsToWav(ordered)
                                    }
                                    else -> local.takeIf { it.isFile && it.length() > 0L }
                                }
                            if (audioFile == null) return@forEach
                            audioFilesToUpload.add(audioFile)
                            if (file.type == SelectedSourceFile.Type.AUDIO_RECORD) {
                                tempGeneratedAudioFiles.add(audioFile)
                            }
                        }
                        SelectedSourceFile.Type.IMAGE,
                        SelectedSourceFile.Type.DOCUMENT -> {
                            val local = File(file.localPath)
                            if (!local.exists() || local.length() == 0L) return@forEach
                            imageFilesToUpload.add(local)
                        }
                    }
                }

                try {
                    if (audioFilesToUpload.isNotEmpty()) {
                        val transcript = repository.uploadAudioFiles(created.id, audioFilesToUpload)
                        latestTranscriptText = transcript.content
                        // 오디오 업로드 응답에 서버 파일 경로 필드 없음 → serverFilePaths에 저장하지 않음
                    }
                    if (imageFilesToUpload.isNotEmpty()) {
                        val imageResponses =
                            repository.uploadImageFiles(
                                meetingId = created.id,
                                files = imageFilesToUpload,
                                imageType = "image",
                            )
                        imageResponses.forEach { resp ->
                            resp.filePath.trim().takeIf { it.isNotEmpty() }?.let {
                                uploadedServerPaths.add(it)
                            }
                        }
                    }
                } finally {
                    tempGeneratedAudioFiles.forEach { it.delete() }
                }
            }
            val summary =
                withContext(Dispatchers.IO) {
                    repository.generateSummary(created.id)
                }
            val ui = MinutesUiMapper.build(snapshot, latestTranscriptText, summary)
            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.COMPLETED,
                    serverFilePaths = uploadedServerPaths,
                    summary = ui.summary,
                )
            }
            _minutes.value = ui
        } catch (e: Exception) {
            // 실 테스트 모드: 네트워크/서버 오류는 더미 성공 처리하지 않고 실패로 표시한다.
            val message = buildPipelineErrorMessage(e)
            _pipelineError.postValue(message)
            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.FAILED,
                    summary = null,
                )
            }
            _minutes.postValue(null)
        }
    }

    private fun buildPipelineErrorMessage(error: Exception): String {
        if (error is HttpException) {
            return ApiErrorParser.httpMessage(
                error = error,
                fallback = "서버 요청 형식 검증에 실패했습니다.",
                includeCode = true,
            )
        }
        return error.message?.takeIf { it.isNotBlank() } ?: "요약 요청 중 오류가 발생했습니다."
    }

    private fun mergeAudioSegmentsToWav(segments: List<File>): File {
        require(segments.isNotEmpty()) { "No segment files to merge." }
        val output =
            File.createTempFile(
                "moa_merged_${System.currentTimeMillis()}_",
                ".wav",
                segments.first().parentFile,
            )
        FileOutputStream(output).use { fos ->
            // WAV 헤더 자리 확보(44 bytes), 데이터 크기 확정 후 덮어쓴다.
            fos.write(ByteArray(44))
            var totalPcmBytes = 0L
            var sampleRateHz = 16_000
            var channelCount = 1
            var bitsPerSample = 16
            var hasAudio = false

            segments.forEach { segment ->
                val extractor = MediaExtractor()
                var codec: MediaCodec? = null
                try {
                    extractor.setDataSource(segment.absolutePath)
                    val trackIndex =
                        (0 until extractor.trackCount).firstOrNull { idx ->
                            extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") == true
                        } ?: return@forEach
                    extractor.selectTrack(trackIndex)
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@forEach

                    val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val bps =
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                2 -> 16 // ENCODING_PCM_16BIT
                                4 -> 32 // ENCODING_PCM_FLOAT
                                else -> 16
                            }
                        } else {
                            16
                        }

                    if (!hasAudio) {
                        sampleRateHz = sr
                        channelCount = ch
                        bitsPerSample = bps
                        hasAudio = true
                    } else {
                        require(sampleRateHz == sr && channelCount == ch) {
                            "Segment audio format mismatch."
                        }
                    }

                    codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()

                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false

                    while (!outputDone) {
                        if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0,
                                    )
                                    extractor.advance()
                                }
                            }
                        }

                        val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                        when {
                            outputIndex >= 0 -> {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null && info.size > 0) {
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    val chunk = ByteArray(info.size)
                                    outputBuffer.get(chunk)
                                    fos.write(chunk)
                                    totalPcmBytes += info.size.toLong()
                                }
                                codec.releaseOutputBuffer(outputIndex, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val outFormat = codec.outputFormat
                                if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    bitsPerSample =
                                        when (outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                            2 -> 16
                                            4 -> 32
                                            else -> bitsPerSample
                                        }
                                }
                            }
                        }
                    }
                } finally {
                    try {
                        codec?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        codec?.release()
                    } catch (_: Exception) {
                    }
                    extractor.release()
                }
            }

            require(totalPcmBytes > 0L) { "No decodable PCM data from audio segments." }
            writeWavHeader(
                output = output,
                pcmDataSize = totalPcmBytes,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                bitsPerSample = bitsPerSample,
            )
        }
        return output
    }

    private fun writeWavHeader(
        output: File,
        pcmDataSize: Long,
        sampleRateHz: Int,
        channelCount: Int,
        bitsPerSample: Int,
    ) {
        val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        RandomAccessFile(output, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE((36L + pcmDataSize).toInt())
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)
            // PCM(1) / Float(3)
            raf.writeShortLE(if (bitsPerSample == 32) 3 else 1)
            raf.writeShortLE(channelCount.toShort().toInt())
            raf.writeIntLE(sampleRateHz)
            raf.writeIntLE(byteRate)
            raf.writeShortLE(blockAlign.toShort().toInt())
            raf.writeShortLE(bitsPerSample.toShort().toInt())
            raf.writeBytes("data")
            raf.writeIntLE(pcmDataSize.toInt())
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }
}

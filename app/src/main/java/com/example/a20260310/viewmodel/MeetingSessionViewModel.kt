package com.example.a20260310.viewmodel

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.a20260310.data.local.MeetingLocalFilesPrefs
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
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale


data class SelectedSourceFile(
    val id: String = UUID.randomUUID().toString(),
    val type: Type,
    val displayName: String,
    val localPath: String,
    val segmentLocalPaths: List<String> = emptyList(),
) {
    enum class Type { AUDIO_RECORD, AUDIO_UPLOAD, IMAGE, DOCUMENT }
}

enum class SummaryPanelPhase {
    RUNNING,
    COMPLETED,
}

enum class SummaryCompletionMode {
    NONE,
    REAL_SUCCESS,
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

private data class QueuedSummaryJob(
    val requestId: String,
    val draft: MeetingDraft,
    val files: List<SelectedSourceFile>,
    val meetingTitle: String,
    val estimateMs: Long,
)

class MeetingSessionViewModel(
    application: Application,
    private val repository: MeetingRepository = MeetingRepository(),
) : AndroidViewModel(application) {

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

    private val _currentMeeting = MutableLiveData<Meeting?>(null)
    val currentMeeting: LiveData<Meeting?> = _currentMeeting

    private val _currentMeetingTitle = MutableLiveData("회의")
    val currentMeetingTitle: LiveData<String> = _currentMeetingTitle

    private val _currentBackendMeetingId = MutableLiveData<Int?>(null)
    val currentBackendMeetingId: LiveData<Int?> = _currentBackendMeetingId

    private val serverFilePathsByMeetingId = mutableMapOf<Int, List<String>>()

    fun rememberServerFilePaths(meetingId: Int, paths: List<String>) {
        if (meetingId <= 0) return
        serverFilePathsByMeetingId[meetingId] = paths.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getServerFilePaths(meetingId: Int): List<String> {
        if (meetingId <= 0) return emptyList()
        serverFilePathsByMeetingId[meetingId]?.let { return it }
        val backendId = _currentBackendMeetingId.value
        val meeting = _currentMeeting.value
        if (meeting != null && backendId == meetingId) {
            return meeting.serverFilePaths.map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }

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
        _currentMeeting.value = transform(cur)
    }

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
        _currentMeeting.value = created
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

    fun clearNewMeetingAttachmentSelection() {
        _selectedFiles.value = emptyList()
    }

    fun hasSelectedFilesForSummary(): Boolean = _selectedFiles.value.orEmpty().isNotEmpty()

    private fun waitingCountSnapshot(): Int = synchronized(queueLock) { waitingQueue.size }

    fun startSummarizePipeline() {
        Log.d("MOA", "startSummarizePipeline selectedFiles=${_selectedFiles.value?.size}")

        if (!hasSelectedFilesForSummary()) {
            Log.d("MOA", "no selected files")
            return
        }

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

        var shouldLaunchProcessor = false

        synchronized(queueLock) {
            waitingQueue.addLast(job)
            Log.d("MOA", "enqueue job id=${job.requestId} files=${filesSnapshot.size} estimateMs=$estimateMs")

            if (!queueProcessorRunning) {
                queueProcessorRunning = true
                shouldLaunchProcessor = true
            }
        }

        bumpWaitingUiAfterEnqueue()

        if (shouldLaunchProcessor) {
            launchQueueProcessor()
        }
    }

    private fun launchQueueProcessor() {
        viewModelScope.launch {
            try {
                Log.d("MOA", "queueProcessor launched")

                while (true) {
                    val job =
                        synchronized(queueLock) {
                            if (waitingQueue.isEmpty()) {
                                queueProcessorRunning = false
                                null
                            } else {
                                waitingQueue.removeFirst().also { runningJob = it }
                            }
                        } ?: break

                    try {
                        Log.d("MOA", "runQueuedJob start requestId=${job.requestId}")
                        runQueuedJob(job)
                    } catch (t: Throwable) {
                        Log.e("MOA", "runQueuedJob failed requestId=${job.requestId}", t)
                    } finally {
                        synchronized(queueLock) {
                            runningJob = null
                        }
                    }
                }

                Log.d("MOA", "queueProcessor finished")
            } finally {
                synchronized(queueLock) {
                    if (waitingQueue.isEmpty()) {
                        queueProcessorRunning = false
                    } else if (!queueProcessorRunning) {
                        queueProcessorRunning = true
                        viewModelScope.launch { launchQueueProcessor() }
                    }
                }
            }
        }
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
                Log.d("MOA","ensureQueueProcessor start, queueProcessorRunning=$queueProcessorRunning")
                while (true) {
                    Log.d("MOA","ensureQueueProcessor summerizing, queueProcessorRunning=$queueProcessorRunning")
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
                Log.d("MOA","ensureQueueProcessor end, queueProcessorRunning=$queueProcessorRunning")
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
            performSummarizePipeline(job.draft, job.files)
            ticker.cancel()
        } finally {
            ticker.cancel()
            _isPipelineRunning.postValue(false)

            val mins = _minutes.value
            if (mins != null) {
                _summaryProgress.postValue(
                    SummaryProgressState(
                        meetingTitle = job.meetingTitle,
                        progressPercent = 100,
                        etaSecondsRemaining = 0L,
                        isRunning = false,
                        isComplete = true,
                        phase = SummaryPanelPhase.COMPLETED,
                        errorMessage = _pipelineError.value,
                        summarySucceeded = true,
                        waitingCount = waitingCountSnapshot(),
                        completionMode = SummaryCompletionMode.REAL_SUCCESS,
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
            totalAudioDurationMs >= 3_600_000L -> 0.20
            totalAudioDurationMs >= 1_800_000L -> 0.15
            else -> 0.10
        }
        val fromAudio = (totalAudioDurationMs * audioFactor).toLong()
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
            15_000L
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun performSummarizePipeline(
        snapshot: MeetingDraft,
        selected: List<SelectedSourceFile>,
    ) {
        _pipelineError.value = null
        ensureMeetingFromDraft(snapshot)
        patchCurrentMeeting { it.copy(status = MeetingStatus.PROCESSING) }

        try {
            val uploadedServerPaths = mutableListOf<String>()
            val serverDate = toServerDate(snapshot.date)
            val serverTime = toServerTime(snapshot.time)

            Log.d(
                "MOA",
                "createMeeting request title=${snapshot.title} date=${snapshot.date}->$serverDate time=${snapshot.time}->$serverTime attendeesCount=${snapshot.participantList().size} attendees=${snapshot.participantList()}"
            )

            val created = try {
                withContext(Dispatchers.IO) {
                    repository.createMeeting(
                        title = snapshot.title.ifBlank { "무제 회의" },
                        folderId = snapshot.folderId,
                        meetingDate = serverDate,
                        meetingTime = serverTime,
                        attendees = snapshot.participantList(),
                        description = null,
                    )
                }
            } catch (e: HttpException) {
                val errorText = try {
                    e.response()?.errorBody()?.string()
                } catch (readError: Exception) {
                    "errorBody read failed: ${readError.message}"
                }

                Log.e(
                    "MOA",
                    "createMeeting HttpException code=${e.code()} message=${e.message()} errorBody=$errorText",
                    e
                )
                throw e
            }

            _currentBackendMeetingId.postValue(created.id)

            val appContext = getApplication<Application>().applicationContext
//            val selectedFolder =
//                MeetingLocalFilesPrefs.prefs(appContext)
//                    .getString("selected_folder", null)
//                    ?.trim()
//                    .orEmpty()
//
//            if (selectedFolder.isNotBlank()) {
//                MeetingLocalFilesPrefs.saveMeetingFolder(
//                    context = appContext,
//                    meetingId = created.id,
//                    folderName = selectedFolder,
//                )
//            }

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
                        Log.d("MOA", "uploadAudioFiles start count=${audioFilesToUpload.size}")
                        val transcript = repository.uploadAudioFiles(created.id, audioFilesToUpload)
                        Log.d("MOA", "uploadAudioFiles success")
                        latestTranscriptText = transcript.content
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
                    rememberServerFilePaths(created.id, uploadedServerPaths.toList())
                } finally {
                    tempGeneratedAudioFiles.forEach { it.delete() }
                }
            }

            Log.d("MOA", "generateSummary start")
            withContext(Dispatchers.IO) {
                repository.generateSummary(created.id)
            }
            Log.d("MOA", "generateSummary success")

            Log.d("MOA", "getSummary start")
            val summaryDetail =
                withContext(Dispatchers.IO) {
                    repository.getSummary(created.id)
                }
            Log.d("MOA", "getSummary success")

            val ui = MinutesUiMapper.build(snapshot, latestTranscriptText, summaryDetail)

            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.COMPLETED,
                    serverFilePaths = uploadedServerPaths,
                    summary = ui.summary,
                )
            }
            _minutes.value = ui
        } catch (e: Exception) {
            Log.e("MOA", "performSummarizePipeline failed", e)
            val message = buildPipelineErrorMessage(e)

            Log.e("MOA", "pipelineError=$message")
            _pipelineError.value = message
            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.FAILED,
                    summary = null,
                )
            }
            _minutes.value = null
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

    private fun toServerDate(input: String): String {
        val value = input.trim()
        if (value.isBlank()) return value

        val normalized = value.replace('/', '-').replace('.', '-')
        val candidates =
            listOf(
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            )

        for (formatter in candidates) {
            try {
                val parsed = LocalDate.parse(normalized, formatter)
                return parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
            }
        }

        return normalized
    }

    private fun toServerTime(input: String): String {
        val value = input.trim()
        if (value.isBlank()) return value

        val koreanLocale = Locale.KOREAN
        val outputFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val formatters =
            listOf(
                DateTimeFormatter.ofPattern("a h:mm", koreanLocale),
                DateTimeFormatter.ofPattern("a hh:mm", koreanLocale),
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
            )

        for (formatter in formatters) {
            try {
                val parsed = LocalTime.parse(value, formatter)
                return parsed.format(outputFormatter)
            } catch (_: Exception) {
            }
        }

        val regex = Regex("""(오전|오후)\s*(\d{1,2}):(\d{2})""")
        val match = regex.matchEntire(value)
        if (match != null) {
            val ampm = match.groupValues[1]
            var hour = match.groupValues[2].toInt()
            val minute = match.groupValues[3]

            if (ampm == "오전") {
                if (hour == 12) hour = 0
            } else {
                if (hour != 12) hour += 12
            }

            return "%02d:%s".format(hour, minute)
        }

        return value
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
                                2 -> 16
                                4 -> 32
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

    private val _selectedFolderId = MutableLiveData<Int?>(null)
    val selectedFolderId: LiveData<Int?> = _selectedFolderId

    private val _selectedFolderName = MutableLiveData<String?>(null)
    val selectedFolderName: LiveData<String?> = _selectedFolderName

    fun setSelectedFolder(id: Int?, name: String?) {
        _selectedFolderId.value = id
        _selectedFolderName.value = name
    }

    fun clearSelectedFolder() {
        _selectedFolderId.value = null
        _selectedFolderName.value = null
    }

    companion object {
        fun factory(
            application: Application,
            repository: MeetingRepository = MeetingRepository(),
        ): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>
                ): T {
                    if (modelClass.isAssignableFrom(MeetingSessionViewModel::class.java)) {
                        return MeetingSessionViewModel(application, repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
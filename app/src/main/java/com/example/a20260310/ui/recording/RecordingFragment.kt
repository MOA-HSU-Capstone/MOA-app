package com.example.a20260310.ui.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.model.RecordingPhase
import com.example.a20260310.data.model.RecordingUiState
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.example.a20260310.viewmodel.RecordingViewModel
import com.example.a20260310.viewmodel.SelectedSourceFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import com.example.a20260310.data.local.LocalMediaFolders
import com.example.a20260310.data.local.MeetingLocalFilesPrefs
import com.example.a20260310.data.model.MeetingFileRow
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getOrCreateFolder(context: Context, folderName: String?): File {
    val baseDir = File(context.filesDir, "MOA")
    if (!baseDir.exists()) baseDir.mkdir()

    val folder = File(baseDir, folderName.orEmpty())
    if (!folder.exists()) folder.mkdir()

    return folder
}

class RecordingFragment : Fragment(R.layout.fragment_recording) {

    companion object {
        private const val STATE_FILE_PATH = "recording_file_path"
        private const val STATE_OUTPUT_NEEDS_NEW = "recording_output_needs_new"
        private const val STATE_SEGMENT_PATHS = "recording_segment_paths"
        private const val TAG = "RecordingFragment"
        /** 파일시스템·UI 안정을 위해 녹음 파일명(확장자 제외) 최대 길이 */
        private const val MAX_RECORDING_STEM_LENGTH = 120
    }

    private val viewModel: RecordingViewModel by viewModels()
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()
    private val gson = Gson()

    private var currentFile: File? = null
    /** 다음 녹음 시작 시 새 파일을 쓸지(세그먼트 경계) */
    private var outputNeedsNewFile: Boolean = true

    /** 확정된 세그먼트 m4a, 녹음 순서 */
    private val recordingSegments: MutableList<File> = mutableListOf()

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaybackActive: Boolean = false
    /** 연속 재생 시 현재 재생 중인 세그먼트 인덱스 ([playbackFiles] 기준) */
    private var playbackSegmentIndex: Int = 0
    private var playbackFiles: List<File> = emptyList()
    private var playbackDurationsMs: List<Long> = emptyList()
    /** 현재 세그먼트 시작 시점의 전역 타임라인 오프셋(ms) */
    private var playbackGlobalOffsetMs: Long = 0L

    private val playbackHandler = Handler(Looper.getMainLooper())
    private val playbackTicker =
        object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (isPlaybackActive && player != null) {
                    val global = playbackGlobalOffsetMs + player.currentPosition.toLong()
                    viewModel.setPlayheadMs(global)
                    playbackHandler.postDelayed(this, 100L)
                }
            }
        }

    /**
     * playheadMs 기준 고정 형식 HH:MM:SS.d (시·분·초 두 자리, 소수 1자리 = 100ms 단위).
     * 선행 0 구간은 gray, 진행 구간은 검정(Spannable).
     */
    private fun buildPlayheadTimerSpannable(context: Context, msRaw: Long): CharSequence {
        val ms = msRaw.coerceAtLeast(0L)
        val tenth = ((ms % 1000L) / 100L).toInt()
        val ss = ((ms % 60_000L) / 1000L).toInt()
        val mm = ((ms % 3_600_000L) / 60_000L).toInt()
        val hh = (ms / 3_600_000L).toInt()

        val text = String.format(Locale.US, "%02d:%02d:%02d.%d", hh, mm, ss, tenth)
        val activeStart =
            when {
                hh > 0 -> 0
                mm > 0 -> 3
                ss > 0 || tenth > 0 -> 6
                else -> 8
            }

        val span = SpannableString(text)
        val gray = ContextCompat.getColor(context, R.color.gray_400)
        val black = ContextCompat.getColor(context, R.color.color_text_primary)
        val end = text.length
        if (activeStart in 1 until end) {
            span.setSpan(ForegroundColorSpan(gray), 0, activeStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (activeStart < end) {
            span.setSpan(ForegroundColorSpan(black), activeStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return span
    }

    private fun hasRecordableContent(state: RecordingUiState): Boolean {
        val hasList = recordingSegments.any { it.exists() && it.length() > 0L }
        val hasCurrent = currentFile?.let { it.exists() && it.length() > 0L } == true
        return hasList || hasCurrent || state.phase == RecordingPhase.RECORDING
    }

    /**
     * 타이머·파형·버튼 아이콘. 재생 중 여부는 isPlaybackActive(녹음 phase와 별개).
     */
    private fun syncRecordingUi(state: RecordingUiState) {
        val v = view ?: return
        val timer = v.findViewById<TextView>(R.id.timer)
        val waveformView = v.findViewById<RecordingWaveformView>(R.id.waveformView)
        val recordInnerDot = v.findViewById<View>(R.id.recordInnerDot)
        val recordButton = v.findViewById<MaterialButton>(R.id.recordButton)
        val playButton = v.findViewById<MaterialButton>(R.id.playButton)
        val saveButton = v.findViewById<MaterialButton>(R.id.saveButton)

        val timerSpan = buildPlayheadTimerSpannable(timer.context, state.playheadMs)
        if (timer.text?.toString() != timerSpan.toString()) {
            timer.setText(timerSpan, TextView.BufferType.SPANNABLE)
        }

        waveformView.bind(
            samples = state.waveformSamples,
            playheadMs = state.playheadMs,
            totalRecordedMs = state.totalRecordedMs,
            phase = state.phase,
        )

        when (state.phase) {
            RecordingPhase.RECORDING -> {
                recordInnerDot.isVisible = false
                recordButton.setIconResource(R.drawable.ic_pause)
            }
            else -> {
                recordInnerDot.isVisible = true
                recordButton.setIcon(null)
            }
        }

        playButton.setIconResource(
            if (isPlaybackActive) R.drawable.ic_pause else R.drawable.ic_play,
        )

        val hasFile = hasRecordableContent(state)
        playButton.isEnabled =
            state.phase == RecordingPhase.RECORDING || hasFile
        saveButton.isEnabled =
            state.phase == RecordingPhase.RECORDING ||
            state.phase == RecordingPhase.PAUSED ||
            hasFile
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val timer = view.findViewById<TextView>(R.id.timer)
        val title = view.findViewById<TextView>(R.id.title)
        val playButton = view.findViewById<MaterialButton>(R.id.playButton)
        val recordButton = view.findViewById<MaterialButton>(R.id.recordButton)
        val recordInnerDot = view.findViewById<View>(R.id.recordInnerDot)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)
        val waveformView = view.findViewById<RecordingWaveformView>(R.id.waveformView)

        if (savedInstanceState == null) {
            viewModel.resetSession()
            currentFile = null
            outputNeedsNewFile = true
            recordingSegments.clear()
        } else {
            savedInstanceState.getString(STATE_FILE_PATH)?.let { currentFile = File(it) }
            outputNeedsNewFile = savedInstanceState.getBoolean(STATE_OUTPUT_NEEDS_NEW, true)
            savedInstanceState.getStringArrayList(STATE_SEGMENT_PATHS)?.let { paths ->
                recordingSegments.clear()
                paths.mapTo(recordingSegments) { File(it) }
            }
        }

        ensureAudioPermission()

        sessionViewModel.meetingDraft.observe(viewLifecycleOwner) {
            title.text = it.title.ifBlank { getString(R.string.nav_recording) }
        }

        waveformView.onPlayheadDeltaMs = { deltaMs ->
            viewModel.adjustPlayheadByMs(deltaMs)
        }

        waveformView.onInteractionBegin = {
            if (isPlaybackActive) {
                pausePlayback()
                viewModel.uiState.value?.let { syncRecordingUi(it) }
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            syncRecordingUi(state)
        }

        recordButton.setOnClickListener {
            if (!hasAudioPermission()) {
                ensureAudioPermission()
                return@setOnClickListener
            }
            val state = viewModel.uiState.value ?: return@setOnClickListener
            when (state.phase) {
                RecordingPhase.RECORDING -> {
                    stopPlaybackIfRunning()
                    viewModel.finalizeCurrentSegment()
                    commitCurrentSegmentToList()
                    outputNeedsNewFile = true
                }
                RecordingPhase.PAUSED -> {
                    stopPlaybackIfRunning()
                    val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
                    val folderName = prefs.getString("selected_folder", "전체")
                    val folder = getOrCreateFolder(requireContext(), folderName)
                    val file = createNewRecordingOutputFile(folder)
                    val fileName = file.name
                    val meetingName = prefs.getString("current_meeting_name", fileName)
                    currentFile = file
                    outputNeedsNewFile = false
                    prefs.edit()
                        .putString(fileName, meetingName)
                        .putString("${fileName}_folder", folderName)
                        .apply()
                    viewModel.beginRecording(file.absolutePath)
                }
                RecordingPhase.IDLE -> {
                    stopPlaybackIfRunning()
                    val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
                    if (outputNeedsNewFile || currentFile == null) {
                        val folderName = prefs.getString("selected_folder", "전체")
                        val folder = getOrCreateFolder(requireContext(), folderName)
                        val file = createNewRecordingOutputFile(folder)
                        val fileName = file.name
                        val meetingName = prefs.getString("current_meeting_name", fileName)
                        currentFile = file
                        outputNeedsNewFile = false
                        prefs.edit()
                            .putString(fileName, meetingName)
                            .putString("${fileName}_folder", folderName)
                            .apply()
                    }
                    val file = currentFile ?: return@setOnClickListener
                    viewModel.beginRecording(file.absolutePath)
                }
            }
        }

        playButton.setOnClickListener {
            if (isPlaybackActive) {
                pausePlayback()
                viewModel.uiState.value?.let { syncRecordingUi(it) }
                return@setOnClickListener
            }
            val before = viewModel.uiState.value ?: return@setOnClickListener
            if (before.phase == RecordingPhase.RECORDING) {
                stopPlaybackIfRunning()
                viewModel.finalizeCurrentSegment()
                commitCurrentSegmentToList()
                outputNeedsNewFile = true
            }
            val latest = viewModel.uiState.value ?: before
            val nearEndThresholdMs = 300L
            val shouldRestartFromZero =
                latest.totalRecordedMs > 0L &&
                latest.playheadMs >= (latest.totalRecordedMs - nearEndThresholdMs).coerceAtLeast(0L)
            val fromStart = (before.phase == RecordingPhase.RECORDING) || shouldRestartFromZero
            if (fromStart) {
                viewModel.setPlayheadMs(0L)
            }
            startPlaybackFromPlayhead(fromStart = fromStart)
            viewModel.uiState.value?.let { syncRecordingUi(it) }
        }

        saveButton.setOnClickListener {
            showSaveDialog()
        }
    }

    private fun commitCurrentSegmentToList() {
        val f = currentFile ?: return
        if (!f.exists() || f.length() <= 0L) return
        if (recordingSegments.lastOrNull()?.absolutePath == f.absolutePath) return
        recordingSegments.add(f)
    }

    private fun syncPlayheadFromMediaPlayer() {
        val player = mediaPlayer ?: return
        try {
            if (playbackDurationsMs.isNotEmpty() && playbackSegmentIndex < playbackDurationsMs.size) {
                val global = playbackGlobalOffsetMs + player.currentPosition.toLong()
                viewModel.setPlayheadMs(global.coerceAtLeast(0L))
            } else {
                viewModel.setPlayheadMs(player.currentPosition.toLong().coerceAtLeast(0L))
            }
        } catch (_: IllegalStateException) {
            // prepare 전 등 currentPosition 불가
        }
    }

    /**
     * MediaPlayer 정리 전에 playheadMs 동기화 → release.
     * pause()/stop()만 호출하지 않고 release까지 하는 이유: prepareAsync 직후 등에서도 재생이 이어지지 않도록 단일 인스턴스를 확실히 끊기 위함.
     */
    private fun releasePlaybackEngine() {
        playbackHandler.removeCallbacks(playbackTicker)
        syncPlayheadFromMediaPlayer()
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlaybackActive = false
    }

    private fun pausePlayback() {
        releasePlaybackEngine()
    }

    private fun releasePlayer() {
        releasePlaybackEngine()
    }

    private fun stopPlaybackIfRunning() {
        releasePlaybackEngine()
        playbackFiles = emptyList()
        playbackDurationsMs = emptyList()
        playbackSegmentIndex = 0
        playbackGlobalOffsetMs = 0L
    }

    private fun alignedPlaybackLists(): Pair<List<File>, List<Long>> {
        val state = viewModel.uiState.value ?: return Pair(emptyList<File>(), emptyList<Long>())
        val files = recordingSegments.filter { it.exists() && it.length() > 0L }
        val dur = state.completedSegmentDurationsMs
        if (files.isEmpty()) return Pair(emptyList<File>(), emptyList<Long>())
        val n = minOf(files.size, dur.size)
        if (n == 0) return Pair(emptyList<File>(), emptyList<Long>())
        return Pair(files.take(n), dur.take(n))
    }

    private fun indexAndLocalMs(globalMs: Long, durations: List<Long>): Pair<Int, Long> {
        if (durations.isEmpty()) return 0 to 0L
        var acc = 0L
        for (i in durations.indices) {
            val d = durations[i]
            if (globalMs < acc + d) {
                return i to (globalMs - acc).coerceAtLeast(0L)
            }
            acc += d
        }
        val last = durations.lastIndex
        return last to durations[last]
    }

    private fun startPlaybackFromPlayhead(fromStart: Boolean = false) {
        releasePlayer()
        val (files, durations) = alignedPlaybackLists()
        val state = viewModel.uiState.value
        if (files.isEmpty() || durations.isEmpty()) {
            val file =
                currentFile ?: state?.outputPath?.let { path ->
                    File(path).also { currentFile = it }
                }
            if (file == null || !file.exists() || file.length() == 0L) {
                Log.e(TAG, "Playback precheck failed: no segment files")
                Toast.makeText(requireContext(), R.string.recording_no_file, Toast.LENGTH_SHORT).show()
                return
            }
            val fromMs = if (fromStart) 0L else (state?.playheadMs ?: 0L)
            startSingleFilePlayback(file, fromMs)
            return
        }

        playbackFiles = files
        playbackDurationsMs = durations
        val totalMs = durations.sum().coerceAtLeast(1L)
        val rawFromMs = if (fromStart) 0L else (state?.playheadMs ?: 0L).coerceIn(0L, totalMs)
        val nearEndThresholdMs = 300L
        val fromMs =
            if (rawFromMs >= (totalMs - nearEndThresholdMs).coerceAtLeast(0L)) 0L else rawFromMs
        if (fromMs == 0L) {
            viewModel.setPlayheadMs(0L)
        }
        val (idx, localMs) = indexAndLocalMs(fromMs, durations)
        playbackSegmentIndex = idx
        playbackGlobalOffsetMs = durations.take(idx).sum()
        playSegmentAtIndex(idx, localMs)
    }

    private fun startSingleFilePlayback(file: File, fromMs: Long) {
        Log.d(TAG, "Playback start single: path=${file.absolutePath}, size=${file.length()}, fromMs=$fromMs")
        val mp = MediaPlayer()
        mediaPlayer = mp
        playbackGlobalOffsetMs = 0L
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { p ->
                val dur = p.duration.coerceAtLeast(1)
                val seekMs = fromMs.coerceIn(0L, dur.toLong())
                p.seekTo(seekMs, MediaPlayer.SEEK_CLOSEST)
                p.start()
                isPlaybackActive = true
                playbackHandler.removeCallbacks(playbackTicker)
                playbackHandler.post(playbackTicker)
                view?.findViewById<MaterialButton>(R.id.playButton)?.setIconResource(R.drawable.ic_pause)
            }
            mp.setOnCompletionListener {
                isPlaybackActive = false
                playbackHandler.removeCallbacks(playbackTicker)
                releasePlayer()
                viewModel.setPlayheadMs(viewModel.uiState.value?.totalRecordedMs ?: 0L)
                view?.findViewById<MaterialButton>(R.id.playButton)?.setIconResource(R.drawable.ic_play)
            }
            mp.setOnErrorListener { _, _, _ ->
                Log.e(TAG, "MediaPlayer onError: path=${file.absolutePath}")
                Toast.makeText(requireContext(), R.string.recording_playback_failed, Toast.LENGTH_SHORT).show()
                isPlaybackActive = false
                playbackHandler.removeCallbacks(playbackTicker)
                releasePlayer()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Playback exception", e)
            Toast.makeText(requireContext(), R.string.recording_playback_failed, Toast.LENGTH_SHORT).show()
            releasePlayer()
        }
    }

    private fun playSegmentAtIndex(index: Int, localStartMs: Long) {
        if (index !in playbackFiles.indices) {
            Toast.makeText(requireContext(), R.string.recording_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        val file = playbackFiles[index]
        playbackSegmentIndex = index
        playbackGlobalOffsetMs = playbackDurationsMs.take(index).sum()
        Log.d(
            TAG,
            "Playback segment $index/${playbackFiles.size}: path=${file.absolutePath}, localStart=$localStartMs, globalOffset=$playbackGlobalOffsetMs",
        )
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { p ->
                val dur = p.duration.coerceAtLeast(1)
                val seekMs = localStartMs.coerceIn(0L, dur.toLong())
                p.seekTo(seekMs, MediaPlayer.SEEK_CLOSEST)
                p.start()
                isPlaybackActive = true
                playbackHandler.removeCallbacks(playbackTicker)
                playbackHandler.post(playbackTicker)
                view?.findViewById<MaterialButton>(R.id.playButton)?.setIconResource(R.drawable.ic_pause)
            }
            mp.setOnCompletionListener {
                val next = index + 1
                if (next < playbackFiles.size) {
                    playSegmentAtIndex(next, 0L)
                } else {
                    isPlaybackActive = false
                    playbackHandler.removeCallbacks(playbackTicker)
                    releasePlayer()
                    val total = playbackDurationsMs.sum()
                    viewModel.setPlayheadMs(total)
                    view?.findViewById<MaterialButton>(R.id.playButton)?.setIconResource(R.drawable.ic_play)
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                Log.e(TAG, "MediaPlayer onError segment: path=${file.absolutePath}")
                Toast.makeText(requireContext(), R.string.recording_playback_failed, Toast.LENGTH_SHORT).show()
                isPlaybackActive = false
                playbackHandler.removeCallbacks(playbackTicker)
                releasePlayer()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Playback segment exception", e)
            Toast.makeText(requireContext(), R.string.recording_playback_failed, Toast.LENGTH_SHORT).show()
            releasePlayer()
        }
    }

    private fun finalizeRecorderForSave() {
        val state = viewModel.uiState.value ?: return
        when (state.phase) {
            RecordingPhase.RECORDING -> {
                viewModel.finalizeCurrentSegment()
                commitCurrentSegmentToList()
            }
            RecordingPhase.PAUSED,
            RecordingPhase.IDLE,
            -> { }
        }
        outputNeedsNewFile = true
    }

    private fun orderedSegmentPathsForSave(): List<String> {
        val paths = recordingSegments.map { it.absolutePath }.filter {
            val f = File(it)
            f.exists() && f.length() > 0L
        }.toMutableList()
        val cur = currentFile
        if (cur != null && cur.exists() && cur.length() > 0L && paths.none { it == cur.absolutePath }) {
            paths.add(cur.absolutePath)
        }
        return paths
    }

    private fun showSaveDialog() {
        val state = viewModel.uiState.value
        if (state == null || !hasAnySaveableSegment(state)) {
            Toast.makeText(requireContext(), R.string.recording_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        stopPlaybackIfRunning()
        finalizeRecorderForSave()

        val orderedPaths = orderedSegmentPathsForSave()
        if (orderedPaths.isEmpty()) {
            Toast.makeText(requireContext(), R.string.recording_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(pad, pad, pad, pad)
        }

        val input = EditText(requireContext()).apply {
            hint = getString(R.string.recording_save_hint)
            val meetingTitle = sessionViewModel.meetingDraft.value?.title?.trim().orEmpty()
            setText(meetingTitle.ifBlank { "녹음" })
        }

        container.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recording_save_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.recording_save_confirm) { d, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                val displayName =
                    name.ifBlank {
                        sessionViewModel.meetingDraft.value?.title?.trim().orEmpty()
                            .ifBlank { File(orderedPaths.first()).nameWithoutExtension }
                    }

                val meetingTitle =
                    sessionViewModel.currentMeetingTitle.value
                        ?.trim()
                        ?.ifBlank { "회의" }
                        ?: "회의"

                sessionViewModel.addSelectedFile(
                    SelectedSourceFile(
                        type = SelectedSourceFile.Type.AUDIO_RECORD,
                        displayName = displayName,
                        localPath = orderedPaths.first(),
                        segmentLocalPaths = orderedPaths,
                    ),
                )

                saveRecordedFileToMeeting(
                    meetingTitle = meetingTitle,
                    displayName = displayName,
                    localPath = orderedPaths.first(),
                    segmentLocalPaths = orderedPaths,
                )

                val appCtx = requireContext().applicationContext
                val pathsForExport = orderedPaths.toList()
                val exportDisplayName = displayName
                lifecycleScope.launch {
                    val savedName = withContext(Dispatchers.IO) {
                        LocalMediaFolders.exportRecordingSegments(appCtx, exportDisplayName, pathsForExport)
                    }
                    if (savedName != null) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_file_saved_as, savedName),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.local_export_recording_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    d.dismiss()
                    findNavController().navigate(R.id.action_recordingFragment_to_addMethodFragment)
                }
            }
            .show()
    }

    private fun hasAnySaveableSegment(state: RecordingUiState): Boolean {
        if (recordingSegments.any { it.exists() && it.length() > 0L }) return true
        if (currentFile?.let { it.exists() && it.length() > 0L } == true) return true
        return state.phase == RecordingPhase.RECORDING
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFile?.absolutePath?.let { outState.putString(STATE_FILE_PATH, it) }
        outState.putBoolean(STATE_OUTPUT_NEEDS_NEW, outputNeedsNewFile)
        outState.putStringArrayList(
            STATE_SEGMENT_PATHS,
            ArrayList(recordingSegments.map { it.absolutePath }),
        )
    }

    override fun onStop() {
        super.onStop()
        stopPlaybackIfRunning()
    }

    override fun onDestroyView() {
        view?.findViewById<RecordingWaveformView>(R.id.waveformView)?.apply {
            onInteractionBegin = null
            onPlayheadDeltaMs = null
        }
        releasePlayer()
        super.onDestroyView()
    }

    private fun hasAudioPermission() =
        ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /** 회의 생성 시 입력한 제목 등 — 녹음 파일 베이스 이름에 사용 */
    private fun rawTitleForRecordingFileName(): String {
        val draftTitle = sessionViewModel.meetingDraft.value?.title?.trim().orEmpty()
        if (draftTitle.isNotBlank()) return draftTitle

        val sessionTitle = sessionViewModel.currentMeetingTitle.value?.trim().orEmpty()
        if (sessionTitle.isNotBlank()) return sessionTitle

        val fromPrefs =
            requireContext()
                .getSharedPreferences("moa_prefs", 0)
                .getString("current_meeting_name", null)
                ?.trim()
                .orEmpty()
        if (fromPrefs.isNotBlank()) return fromPrefs

        return "녹음"
    }

    private fun sanitizeRecordingStem(raw: String): String {
        var s = raw.replace("""[^\w.\-가-힣]""".toRegex(), "_")
        s = s.trim(' ', '_', '.')
        if (s.endsWith(".m4a", ignoreCase = true)) {
            s = s.dropLast(4).trim(' ', '_', '.')
        }
        if (s.isBlank()) s = "녹음"
        if (s.length > MAX_RECORDING_STEM_LENGTH) {
            s = s.take(MAX_RECORDING_STEM_LENGTH).trimEnd(' ', '_', '.')
            if (s.isBlank()) s = "녹음"
        }
        return s
    }

    private fun uniqueM4aInFolder(folder: File, stem: String): File {
        val base = "$stem.m4a"
        val dot = base.lastIndexOf('.')
        val nameStem = base.substring(0, dot)
        val ext = base.substring(dot)
        var candidate = File(folder, base)
        var n = 1
        while (candidate.exists()) {
            candidate = File(folder, "${nameStem}_$n$ext")
            n++
        }
        return candidate
    }

    private fun createNewRecordingOutputFile(folder: File): File {
        val stem = sanitizeRecordingStem(rawTitleForRecordingFileName())
        return uniqueM4aInFolder(folder, stem)
    }

    private fun ensureAudioPermission() {
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    private fun saveRecordedFileToMeeting(
        meetingTitle: String,
        displayName: String,
        localPath: String,
        segmentLocalPaths: List<String>,
    ) {
        val firstExistingFile = sequenceOf(localPath)
            .plus(segmentLocalPaths.asSequence())
            .map { File(it) }
            .firstOrNull { it.exists() && it.length() > 0L }

        val subtitle = firstExistingFile?.let { "${it.length() / 1024} KB" }.orEmpty()

        val newItem =
            MeetingFileRow(
                title =
                    displayName.ifBlank {
                        firstExistingFile?.nameWithoutExtension ?: "녹음"
                    },
                subtitle = subtitle,
                localPath = localPath,
                displayName =
                    displayName.ifBlank {
                        firstExistingFile?.nameWithoutExtension ?: "녹음"
                    },
                type = MeetingFileRow.Type.AUDIO,
            )
        val backendId = sessionViewModel.currentBackendMeetingId.value
        MeetingLocalFilesPrefs.appendOrUpdate(
            requireContext(),
            gson,
            meetingTitle,
            backendId,
            newItem,
        )
    }
}
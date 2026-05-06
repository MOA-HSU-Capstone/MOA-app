package com.example.a20260310.ui.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.example.a20260310.viewmodel.RecordingViewModel
import com.example.a20260310.viewmodel.SelectedSourceFile
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun getCurrentFileName(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "moa_${sdf.format(Date())}.m4a"
}

fun getOrCreateFolder(context: Context, folderName: String?): File {
    val baseDir = File(context.filesDir, "MOA")
    if (!baseDir.exists()) baseDir.mkdir()

    val folder = File(baseDir, folderName)
    if (!folder.exists()) folder.mkdir()

    return folder
}

class RecordingFragment : Fragment(R.layout.fragment_recording) {

    private val viewModel: RecordingViewModel by viewModels()
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    private var currentFile: File? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val timer = view.findViewById<TextView>(R.id.timer)
        val title = view.findViewById<TextView>(R.id.title)
        val recordBtn = view.findViewById<MaterialButton>(R.id.recordButton)
        val waveformView = view.findViewById<RecordingWaveformView>(R.id.waveformView)

        ensureAudioPermission()

        // 제목 표시
        sessionViewModel.meetingDraft.observe(viewLifecycleOwner) {
            title.text = it.title
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val h = state.elapsedSeconds / 3600
            val m = (state.elapsedSeconds % 3600) / 60
            val s = state.elapsedSeconds % 60
            timer.text = String.format("%02d:%02d:%02d", h, m, s)

            waveformView.updateAmplitude(state.amplitude, state.isRecording)
        }

        recordBtn.setOnClickListener {

            if (!hasAudioPermission()) {
                ensureAudioPermission()
                return@setOnClickListener
            }

            val state = viewModel.uiState.value
            val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
            // 🔥 녹음 중 → 종료 + 이동
            if (state?.isRecording == true) {

                viewModel.stopRecording()

                currentFile?.let { file ->
                    if (file.exists() && file.length() > 0) {

                        sessionViewModel.addSelectedFile(
                            SelectedSourceFile(
                                type = SelectedSourceFile.Type.AUDIO_RECORD,
                                displayName = sessionViewModel.meetingDraft.value?.title ?: "녹음",
                                localPath = file.absolutePath
                            )
                        )

                        findNavController().navigate(
                            R.id.action_recordingFragment_to_summarizingFragment
                        )

                    } else {
                        Toast.makeText(requireContext(), "녹음 파일 없음", Toast.LENGTH_SHORT).show()
                    }
                }

                return@setOnClickListener
            }

            // 🔥 녹음 시작
            val fileName = getCurrentFileName()
            //val file = File(requireContext().filesDir, fileName)
                //currentFile = file

            val meetingName = prefs.getString("current_meeting_name", fileName)
            val folderName = prefs.getString("selected_folder", "전체")
            val safeFolderName = folderName ?: "default"
            val folder = getOrCreateFolder(requireContext(), safeFolderName)
            val file = File(folder, fileName)

            currentFile = file

            // 🔥 매핑 저장
            prefs.edit()
                .putString(fileName, meetingName)
                .putString("${fileName}_folder", folderName)
                .apply()

            viewModel.toggleRecording(outputPath = file.absolutePath)
        }
    }

    private fun hasAudioPermission() =
        ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureAudioPermission() {
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }
}
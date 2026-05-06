package com.example.a20260310.ui.add

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.example.a20260310.viewmodel.SelectedSourceFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AddMethodFragment : Fragment(R.layout.fragment_add_method) {

    companion object {
        private val DOCUMENT_PICKER_MIME_TYPES = arrayOf(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
        )
        private val ALLOWED_DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
        )
        private val ALLOWED_DOCUMENT_EXTENSIONS = setOf("pdf", "png", "jpg", "jpeg", "webp")
    }
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    private val scanDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageCount = scanResult?.pages?.size ?: 0
            val firstPage = scanResult?.pages?.firstOrNull()
            val imagePath = firstPage?.imageUri?.let { copyUriToAppFile(it, "scan_${System.currentTimeMillis()}.jpg") }
            if (imagePath != null) {
                sessionViewModel.addSelectedFile(
                    SelectedSourceFile(
                        type = SelectedSourceFile.Type.IMAGE,
                        displayName = "사진",
                        localPath = imagePath,
                    ),
                )
            }
            Toast.makeText(requireContext(), "스캔 완료: ${pageCount}페이지", Toast.LENGTH_SHORT).show()
        }

    private val pickDocumentFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val displayName = resolveDisplayName(uri)
                if (!isAllowedDocumentUri(uri, displayName)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.add_document_mime_rejected),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val outputPath = withContext(Dispatchers.IO) { copyPickedDocumentToAppStorage(uri, displayName) }
                if (outputPath != null) {
                    sessionViewModel.addSelectedFile(
                        SelectedSourceFile(
                            type = SelectedSourceFile.Type.DOCUMENT,
                            displayName = displayName,
                            localPath = outputPath,
                        ),
                    )
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.add_document_added, displayName),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "문서 파일을 읽지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val pickAudioFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val displayName = resolveDisplayName(uri)
                val outputPath = withContext(Dispatchers.IO) { copyPickedAudioToAppStorage(uri, displayName) }
                if (outputPath != null) {
                    sessionViewModel.addSelectedFile(
                        SelectedSourceFile(
                            type = SelectedSourceFile.Type.AUDIO_UPLOAD,
                            displayName = displayName,
                            localPath = outputPath,
                        ),
                    )
                    Toast.makeText(
                        requireContext(),
                        "선택한 파일: $displayName",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "오디오 파일을 읽지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selectedContainer = view.findViewById<LinearLayout>(R.id.selectedFilesContainer)
        val selectedFilesScroll = view.findViewById<View>(R.id.selectedFilesScroll)
        val selectedEmptyState = view.findViewById<LinearLayout>(R.id.selectedEmptyState)

        view.findViewById<MaterialCardView>(R.id.cardRecord).setOnClickListener {
            findNavController().navigate(R.id.action_addMethodFragment_to_recordingFragment)
        }
        view.findViewById<MaterialCardView>(R.id.cardUploadAudio).setOnClickListener {
            pickAudioFile.launch(arrayOf("audio/*"))
        }
        view.findViewById<MaterialCardView>(R.id.cardCapture).setOnClickListener {
            startDocumentScan()
        }
        view.findViewById<MaterialCardView>(R.id.cardUploadText).setOnClickListener {
            pickDocumentFile.launch(DOCUMENT_PICKER_MIME_TYPES)
        }

        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {
            if (!sessionViewModel.hasSelectedFilesForSummary()) {
                Toast.makeText(requireContext(), "요약할 파일을 먼저 추가해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sessionViewModel.clearPipelineError()
            findNavController().navigate(R.id.action_addMethodFragment_to_summarizingFragment)
        }

        sessionViewModel.selectedFiles.observe(viewLifecycleOwner) { files ->
            selectedContainer.removeAllViews()
            val empty = files.isEmpty()
            selectedFilesScroll.visibility = if (empty) View.GONE else View.VISIBLE
            selectedEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
            files.forEach { file ->
                selectedContainer.addView(createSelectedCard(file))
            }
        }
    }

    private fun isAllowedDocumentUri(uri: Uri, displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in ALLOWED_DOCUMENT_EXTENSIONS) return true
        val mime = requireContext().contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        return mime != null && mime in ALLOWED_DOCUMENT_MIME_TYPES
    }

    private fun copyPickedDocumentToAppStorage(uri: Uri, displayName: String): String? {
        val safeName = displayName.replace("""[^\w.\-가-힣]""".toRegex(), "_")
        val outputFile = File(requireContext().getExternalFilesDir(null), "${System.currentTimeMillis()}_$safeName")
        return runCatching {
            requireContext().contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun copyPickedAudioToAppStorage(uri: Uri, displayName: String): String? {
        val safeName = displayName.replace("""[^\w.\-가-힣]""".toRegex(), "_")
        val outputFile = File(requireContext().getExternalFilesDir(null), "${System.currentTimeMillis()}_$safeName")
        return runCatching {
            requireContext().contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = requireContext().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    return cursor.getString(idx).orEmpty().ifBlank { "알 수 없는 파일" }
                }
            }
        }
        return uri.lastPathSegment ?: "알 수 없는 파일"
    }

    private fun startDocumentScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender: IntentSender ->
                scanDocumentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "문서 스캔을 시작하지 못했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
    }

    private fun createSelectedCard(file: SelectedSourceFile): View {
        val card = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_selected_file,
            requireView().findViewById(R.id.selectedFilesContainer),
            false,
        )
        val sizePx = 92.dpToPx()
        val endMarginPx = 16.dpToPx()
        card.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            marginEnd = endMarginPx
        }

        val preview = card.findViewById<ImageView>(R.id.previewImage)
        val name = card.findViewById<TextView>(R.id.nameText)
        val remove = card.findViewById<ImageButton>(R.id.removeButton)

        name.text = when (file.type) {
            SelectedSourceFile.Type.AUDIO_RECORD -> "녹음"
            SelectedSourceFile.Type.AUDIO_UPLOAD -> "음성"
            SelectedSourceFile.Type.IMAGE -> "사진"
            SelectedSourceFile.Type.DOCUMENT -> "문서"
        }
        when (file.type) {
            SelectedSourceFile.Type.AUDIO_RECORD,
            SelectedSourceFile.Type.AUDIO_UPLOAD -> preview.setImageResource(R.drawable.moa_microphone)
            SelectedSourceFile.Type.IMAGE -> {
                val f = File(file.localPath)
                if (f.exists()) preview.setImageURI(Uri.fromFile(f)) else preview.setImageResource(R.drawable.moa_camera)
            }
            SelectedSourceFile.Type.DOCUMENT -> preview.setImageResource(R.drawable.moa_document)
        }

        remove.setOnClickListener { sessionViewModel.removeSelectedFile(file.id) }
        return card
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun copyUriToAppFile(uri: Uri, fileName: String): String? {
        val outputFile = File(requireContext().getExternalFilesDir(null), fileName)
        return runCatching {
            requireContext().contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile.absolutePath
        }.getOrNull()
    }
}

package com.example.a20260310.ui.add

import android.app.Activity
import android.content.Context
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
import com.example.a20260310.data.local.LocalMediaFolders
import com.example.a20260310.data.local.MeetingLocalFilesPrefs
import com.example.a20260310.data.model.MeetingFileRow
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.example.a20260310.viewmodel.SelectedSourceFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

class AddMethodFragment : Fragment(R.layout.fragment_add_method) {

    companion object {
        private val AUDIO_PICKER_MIME_TYPES = arrayOf(
            "audio/wav",
            "audio/x-wav",
            "audio/mpeg",
            "audio/mp4",
            "audio/m4a",
            "audio/*",
        )

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

        private val ALLOWED_AUDIO_MIME_TYPES = setOf(
            "audio/wav",
            "audio/x-wav",
            "audio/mpeg",
            "audio/mp4",
            "audio/m4a",
            "audio/aac",
            "audio/*",
        )

        private val ALLOWED_AUDIO_EXTENSIONS = setOf("wav", "mp3", "m4a", "mp4", "aac")
    }

    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()
    private val gson = Gson()

    private val scanDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            lifecycleScope.launch {
                val ctx = requireContext()
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pageCount = scanResult?.pages?.size ?: 0
                val pdfUri = scanResult?.pdf?.uri
                val firstPage = scanResult?.pages?.firstOrNull()
                val fileUri = pdfUri ?: firstPage?.imageUri

                if (fileUri == null) {
                    Toast.makeText(ctx, getString(R.string.toast_scan_no_file), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val localName =
                    when {
                        pdfUri != null -> "scan.pdf"
                        else -> "scan.jpg"
                    }

                val savedPath =
                    withContext(Dispatchers.IO) {
                        copyUriToAppFile(ctx, fileUri, localName)
                    }
                if (savedPath == null) {
                    Toast.makeText(ctx, getString(R.string.toast_scan_copy_failed), Toast.LENGTH_LONG).show()
                    return@launch
                }

                val file = File(savedPath)
                val mimeType =
                    when {
                        file.extension.equals("pdf", true) -> "application/pdf"
                        file.extension.equals("jpg", true) || file.extension.equals("jpeg", true) -> "image/jpeg"
                        file.extension.equals("png", true) -> "image/png"
                        else -> ctx.contentResolver.getType(fileUri) ?: "application/octet-stream"
                    }

                val fileType = if (mimeType == "application/pdf") "PDF" else "IMAGE"

                sessionViewModel.addSelectedFile(
                    SelectedSourceFile(
                        type = if (fileType == "PDF") SelectedSourceFile.Type.DOCUMENT else SelectedSourceFile.Type.IMAGE,
                        displayName = file.name,
                        localPath = savedPath,
                    ),
                )

                saveFileToMeeting(
                    meetingTitle = getMeetingTitle(),
                    displayName = file.name,
                    localPath = savedPath,
                    fileType = fileType,
                    mimeType = mimeType,
                )

                val appCtx = ctx.applicationContext
                val savedPublicName =
                    withContext(Dispatchers.IO) {
                        when (fileType) {
                            "PDF" -> LocalMediaFolders.exportPdfFile(appCtx, file, file.name)
                            else -> LocalMediaFolders.exportPhotoFile(appCtx, file, file.name)
                        }
                    }

                if (savedPublicName != null) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.toast_scan_saved_pages, savedPublicName, pageCount),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        ctx,
                        getString(R.string.toast_scan_export_failed, pageCount),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

    private val pickDocumentFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch {
                val displayName = resolveDisplayName(uri)
                val mimeType = resolveMimeType(uri, displayName)

                if (!isAllowedDocumentUri(uri, displayName, mimeType)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.add_document_mime_rejected),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }

                val outputPath = withContext(Dispatchers.IO) {
                    copyPickedDocumentToAppStorage(uri, displayName)
                }

                if (outputPath != null) {
                    val fileType = when {
                        mimeType == "application/pdf" || displayName.endsWith(".pdf", true) -> "PDF"
                        mimeType.startsWith("image/") -> "IMAGE"
                        else -> "DOCUMENT"
                    }

                    sessionViewModel.addSelectedFile(
                        SelectedSourceFile(
                            type = when (fileType) {
                                "IMAGE" -> SelectedSourceFile.Type.IMAGE
                                else -> SelectedSourceFile.Type.DOCUMENT
                            },
                            displayName = displayName,
                            localPath = outputPath,
                        ),
                    )

                    saveFileToMeeting(
                        meetingTitle = getMeetingTitle(),
                        displayName = displayName,
                        localPath = outputPath,
                        fileType = fileType,
                        mimeType = mimeType,
                    )

                    var skipDocumentAddedToast = false
                    if (fileType == "IMAGE") {
                        val savedName = withContext(Dispatchers.IO) {
                            LocalMediaFolders.exportPhotoFile(
                                requireContext().applicationContext,
                                File(outputPath),
                                displayName,
                            )
                        }
                        if (savedName != null) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_file_saved_as, savedName),
                                Toast.LENGTH_SHORT,
                            ).show()
                            skipDocumentAddedToast = true
                        } else {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.local_export_photo_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    if (!skipDocumentAddedToast) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.add_document_added, displayName),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "문서 파일을 읽지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val pickAudioFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isEmpty()) return@registerForActivityResult

            lifecycleScope.launch {
                val uniqueUris = LinkedHashSet(uris).toList()
                var success = 0
                var rejected = 0

                uniqueUris.forEach { uri ->
                    val displayName = resolveDisplayName(uri)

                    if (!isAllowedAudioUri(uri, displayName)) {
                        rejected++
                        return@forEach
                    }

                    val outputPath = withContext(Dispatchers.IO) {
                        copyPickedAudioToAppStorage(uri, displayName)
                    }

                    if (outputPath != null) {
                        sessionViewModel.addSelectedFile(
                            SelectedSourceFile(
                                type = SelectedSourceFile.Type.AUDIO_UPLOAD,
                                displayName = displayName,
                                localPath = outputPath,
                            ),
                        )

                        saveFileToMeeting(
                            meetingTitle = getMeetingTitle(),
                            displayName = displayName,
                            localPath = outputPath,
                            fileType = "AUDIO",
                            mimeType = requireContext().contentResolver.getType(uri) ?: "audio/*",
                        )

                        success++
                    }
                }

                val message =
                    when {
                        success > 0 && rejected > 0 -> "오디오 ${success}개 추가, ${rejected}개는 형식이 맞지 않아 제외했어요."
                        success > 0 -> "오디오 파일 ${success}개를 추가했어요."
                        rejected > 0 -> "허용되지 않는 오디오 형식입니다."
                        else -> "오디오 파일을 읽지 못했습니다."
                    }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
            pickAudioFiles.launch(AUDIO_PICKER_MIME_TYPES)
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

    private fun getMeetingTitle(): String = sessionViewModel.currentMeetingTitle.value ?: "회의"

    /**
     * URI에서 복사한 첨부·스캔 파일을 둔다. [Context.getExternalFilesDir]가 아닌
     * [Context.filesDir]만 사용한다 (`Android/data/.../files`에 저장하지 않음).
     */
    private fun importWorkDir(context: Context): File =
        File(context.filesDir, "MOA/imports").apply { mkdirs() }

    private fun importWorkDir(): File = importWorkDir(requireContext())

    /**
     * 원하는 표시 이름에 가깝게 파일명을 정리하고, 같은 폴더에 동일 이름이 있으면 _1, _2… 를 붙인다.
     */
    private fun uniqueOutputFile(parentDir: File, displayName: String): File? {
        val safe = displayName.replace("""[^\w.\-가-힣]""".toRegex(), "_").trim().ifBlank { return null }
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var candidate = File(parentDir, safe)
        var n = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "${stem}_$n$ext")
            n++
        }
        return candidate
    }

    private fun resolveMimeType(uri: Uri, displayName: String): String {
        val resolverMime = requireContext().contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        if (!resolverMime.isNullOrBlank()) return resolverMime

        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun isAllowedDocumentUri(uri: Uri, displayName: String, mimeType: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in ALLOWED_DOCUMENT_EXTENSIONS) return true
        return mimeType in ALLOWED_DOCUMENT_MIME_TYPES || mimeType.startsWith("image/")
    }

    private fun isAllowedAudioUri(uri: Uri, displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in ALLOWED_AUDIO_EXTENSIONS) return true

        val mime = requireContext().contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: return false
        if (mime in ALLOWED_AUDIO_MIME_TYPES) return true
        return mime.startsWith("audio/")
    }

    private fun copyPickedDocumentToAppStorage(uri: Uri, displayName: String): String? {
        val outputFile = uniqueOutputFile(importWorkDir(), displayName) ?: return null
        return runCatching {
            requireContext().contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun copyPickedAudioToAppStorage(uri: Uri, displayName: String): String? {
        val outputFile = uniqueOutputFile(importWorkDir(), displayName) ?: return null
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
                if (idx >= 0) return cursor.getString(idx).orEmpty().ifBlank { "알 수 없는 파일" }
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
                Toast.makeText(requireContext(), error.message ?: "문서 스캔을 시작하지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createSelectedCard(file: SelectedSourceFile): View {
        val card = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_selected_file,
            requireView().findViewById(R.id.selectedFilesContainer),
            false,
        )

        val cardW = 108.dpToPx()
        val cardH = 120.dpToPx()
        val endMarginPx = 16.dpToPx()

        card.layoutParams = LinearLayout.LayoutParams(cardW, cardH).apply { marginEnd = endMarginPx }

        val preview = card.findViewById<ImageView>(R.id.previewImage)
        val name = card.findViewById<TextView>(R.id.nameText)
        val remove = card.findViewById<ImageButton>(R.id.removeButton)

        val fallbackLabel = when (file.type) {
            SelectedSourceFile.Type.AUDIO_RECORD -> "녹음"
            SelectedSourceFile.Type.AUDIO_UPLOAD -> "음성"
            SelectedSourceFile.Type.IMAGE -> "사진"
            SelectedSourceFile.Type.DOCUMENT -> "문서"
        }

        name.text = file.displayName.trim().ifBlank { fallbackLabel }
        preview.scaleType = ImageView.ScaleType.FIT_CENTER

        when (file.type) {
            SelectedSourceFile.Type.AUDIO_RECORD,
            SelectedSourceFile.Type.AUDIO_UPLOAD -> preview.setImageResource(R.drawable.ic_recording)

            SelectedSourceFile.Type.IMAGE -> {
                val f = File(file.localPath)
                if (f.exists()) {
                    preview.setImageURI(null)
                    preview.setImageURI(Uri.fromFile(f))
                } else {
                    preview.setImageResource(R.drawable.ic_camera)
                }
            }

            SelectedSourceFile.Type.DOCUMENT -> preview.setImageResource(R.drawable.ic_document)
        }

        remove.setOnClickListener {
            sessionViewModel.removeSelectedFile(file.id)
            removeFileFromMeeting(file.localPath)
        }

        return card
    }

    private fun Int.dpToPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun copyUriToAppFile(context: Context, uri: Uri, fileName: String): String? {
        val outputFile = uniqueOutputFile(importWorkDir(context), fileName) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun saveFileToMeeting(
        meetingTitle: String,
        displayName: String,
        localPath: String,
        fileType: String,
        mimeType: String,
    ) {
        val file = File(localPath)
        val newItem =
            MeetingFileRow(
                title = displayName.ifBlank { file.name.ifBlank { "첨부파일" } },
                subtitle = if (file.exists()) "${file.length() / 1024} KB" else "",
                localPath = localPath,
                displayName = displayName.ifBlank { file.name.ifBlank { "첨부파일" } },
                type = when (fileType.uppercase(Locale.ROOT)) {
                    "AUDIO" -> MeetingFileRow.Type.AUDIO
                    "IMAGE" -> MeetingFileRow.Type.IMAGE
                    "PDF" -> MeetingFileRow.Type.PDF
                    else -> if (mimeType == "application/pdf" || file.extension.equals("pdf", true)) {
                        MeetingFileRow.Type.PDF
                    } else {
                        MeetingFileRow.Type.DOCUMENT
                    }
                },
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

    private fun removeFileFromMeeting(localPath: String) {
        val meetingTitle = getMeetingTitle()
        val backendId = sessionViewModel.currentBackendMeetingId.value
        MeetingLocalFilesPrefs.removeByLocalPath(
            requireContext(),
            gson,
            meetingTitle,
            backendId,
            localPath,
        )
    }
}
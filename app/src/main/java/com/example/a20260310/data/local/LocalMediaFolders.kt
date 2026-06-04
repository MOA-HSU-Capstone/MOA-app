package com.example.a20260310.data.local

import android.content.Context
import android.util.Log
import java.io.File
/**
 * 공용 내장 저장소에 MOA 미디어를보낸다.
 *
 * - 녹음: [PublicMoaMediaStoreExport] → `Recordings/MOA/`
 * - 사진: [PublicMoaMediaStoreExport] → `Pictures/MOA/`
 * - PDF: [PublicMoaMediaStoreExport] → 공용 문서 `Documents/MOA/`
 */
object LocalMediaFolders {

    private const val TAG = "LocalMediaFolders"

    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "wav", "aac", "mp4", "3gp", "ogg", "flac", "amr")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")

    /**
     * 파일명에 쓸 수 없는 문자만 치환. 경로 구분자는 제거해 디렉터리 탈출을 막는다.
     * 띄어쓰기·탭 등 공백은 `_`로 바꿔 갤러리·파일관리자·서버 목록 간 이름을 맞춘다.
     */
    fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "unnamed" }
        return trimmed
            .replace(Regex("""[/\\:*?"<>|\u0000]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_', '.')
            .take(180)
            .ifBlank { "unnamed" }
    }

    private fun stripKnownTrailingExtension(name: String, known: Set<String>): Pair<String, String?> {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return name to null
        val ext = name.substring(dot + 1).lowercase()
        return if (ext in known) name.substring(0, dot) to ext else name to null
    }

    private fun stripTrailingRecordingFileLabel(title: String): String =
        title.replace(Regex("""(?i)[\s_]*녹음파일\s*$"""), "").trim()

    private fun fauxProbeForExport(displayName: String): File {
        val t = displayName.trim().ifBlank { return File("/", "x.m4a") }
        val dot = t.lastIndexOf('.')
        val hasReasonableExt = dot > 0 && dot < t.length - 1
        return File("/", if (hasReasonableExt) t else "$t.m4a")
    }

    /**
     * 공용 저장·MediaStore 조회 시 서버/목록에 올 수 있는 표기 차이(공백·`녹음파일` 접미사 등)를 흡수한다.
     */
    fun audioExportDisplayNamesForLookup(apiDisplayName: String): List<String> {
        val faux = fauxProbeForExport(apiDisplayName)
        val set = linkedSetOf<String>()
        set.add(buildAudioExportFileName(apiDisplayName, faux))
        set.add(buildAudioExportFileNameInternal(apiDisplayName, faux, stripRecordingSuffix = false))
        val raw = apiDisplayName.trim()
        if (raw.isNotEmpty()) {
            set.add(raw)
            if (raw.contains(' ') || raw.contains('\t')) {
                set.add(raw.replace(Regex("""\s+"""), "_"))
            }
        }
        return set.filter { it.isNotBlank() }
    }

    private fun buildAudioExportFileNameInternal(
        userDisplayName: String,
        sourceFile: File,
        stripRecordingSuffix: Boolean,
    ): String {
        val ext = sourceFile.extension.lowercase().ifBlank { "m4a" }
        var raw = userDisplayName.trim().ifBlank { "unnamed" }
        if (stripRecordingSuffix) {
            raw = stripTrailingRecordingFileLabel(raw).ifBlank { "unnamed" }
        }
        var base = sanitizeFileName(raw)
        val (withoutAudioExt, trailingAudio) = stripKnownTrailingExtension(base, AUDIO_EXTENSIONS)
        base = withoutAudioExt
        if (trailingAudio != null && trailingAudio != ext) {
            Log.w(
                TAG,
                "Display name extension .$trailingAudio differs from source .$ext; using source format.",
            )
        }
        return "$base.$ext"
    }

    /**
     * [userDisplayName]은 사용자 입력을 최대한 유지하고, 확장자는 [sourceFile] 형식을 따른다.
     * 끝의 `… 녹음파일` / `…_녹음파일` 접미사는 공용 파일명에서 제거한다.
     */
    fun buildAudioExportFileName(userDisplayName: String, sourceFile: File): String =
        buildAudioExportFileNameInternal(userDisplayName, sourceFile, stripRecordingSuffix = true)

    fun buildPhotoExportFileName(userDisplayName: String, sourceFile: File): String {
        val ext = sourceFile.extension.lowercase().ifBlank { "jpg" }
        var base = sanitizeFileName(userDisplayName)
        val (withoutImageExt, trailingImg) = stripKnownTrailingExtension(base, IMAGE_EXTENSIONS)
        base = withoutImageExt
        if (trailingImg != null && trailingImg != ext) {
            Log.w(
                TAG,
                "Display name extension .$trailingImg differs from source .$ext; using source format.",
            )
        }
        return "$base.$ext"
    }

    /**
     * 녹음을 공용 **`Recordings/MOA/`** 에 한 파일로 저장한다.
     * 세그먼트가 여러 개이면 [AudioSegmentMerger]로 WAV 하나로 합친 뒤 저장한다(업로드 파이프라인과 동일).
     *
     * @return 저장된 파일명(확장자 포함), 실패 시 `null`
     */
    fun exportRecordingSegments(
        context: Context,
        userDisplayName: String,
        segmentPaths: List<String>,
    ): String? {
        val appCtx = context.applicationContext

        val ordered = segmentPaths
            .map { File(it) }
            .filter { it.isFile && it.exists() && it.length() > 0L }
            .distinctBy { it.absolutePath }

        if (ordered.isEmpty()) {
            Log.e(TAG, "exportRecording: no readable segment files")
            return null
        }

        var mergedTemp: File? = null
        val source: File =
            if (ordered.size == 1) {
                ordered.first()
            } else {
                try {
                    AudioSegmentMerger.mergeSegmentsToWav(ordered).also { mergedTemp = it }
                } catch (e: Exception) {
                    Log.e(TAG, "exportRecording: merge failed", e)
                    return null
                }
            }

        val destName = buildAudioExportFileName(userDisplayName, source)
        return try {
            val ok = PublicMoaMediaStoreExport.exportAudio(appCtx, source, destName)
            if (!ok) {
                Log.e(TAG, "exportRecording: public save failed for $destName")
                null
            } else {
                destName
            }
        } finally {
            mergedTemp?.delete()
        }
    }

    /**
     * 사진(이미지)을 공용 **`Pictures/MOA/`** 에 저장한다.
     *
     * @return 저장된 파일명(확장자 포함), 실패 시 `null`
     */
    fun exportPhotoFile(
        context: Context,
        sourceFile: File,
        preferredDisplayName: String?,
    ): String? {
        val appCtx = context.applicationContext
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportPhoto: invalid source: ${sourceFile.absolutePath}")
            return null
        }

        val nameForExport = preferredDisplayName?.trim()?.ifBlank { null } ?: sourceFile.name
        val destName = buildPhotoExportFileName(nameForExport, sourceFile)
        return if (PublicMoaMediaStoreExport.exportImage(appCtx, sourceFile, destName)) {
            destName
        } else {
            null
        }
    }

    fun buildPdfExportFileName(userDisplayName: String, sourceFile: File): String {
        var base = sanitizeFileName(userDisplayName)
        if (base.lowercase().endsWith(".pdf")) {
            base = base.dropLast(4).trimEnd('.')
        }
        if (base.isBlank()) base = sourceFile.nameWithoutExtension.ifBlank { "scan" }
        return "$base.pdf"
    }

    /**
     * PDF를 공용 문서 **`Documents/MOA/`** 에 저장한다.
     * @return 저장된 파일명, 실패 시 `null`
     */
    fun exportPdfFile(
        context: Context,
        sourceFile: File,
        preferredDisplayName: String?,
    ): String? {
        val appCtx = context.applicationContext
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportPdf: invalid source: ${sourceFile.absolutePath}")
            return null
        }
        val nameForExport = preferredDisplayName?.trim()?.ifBlank { null } ?: sourceFile.name
        val destName = buildPdfExportFileName(nameForExport, sourceFile)
        return if (PublicMoaMediaStoreExport.exportPdfToDocumentsMoa(appCtx, sourceFile, destName)) {
            destName
        } else {
            null
        }
    }

    /**
     * 서버 등에서 받은 오디오를 공용 **`Recordings/MOA/`** 에 저장한다.
     * @return 저장된 파일명(확장자 포함), 실패 시 `null`
     */
    fun exportDownloadedAudio(
        context: Context,
        sourceFile: File,
        preferredDisplayName: String?,
    ): String? {
        val appCtx = context.applicationContext
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportDownloadedAudio: invalid source: ${sourceFile.absolutePath}")
            return null
        }
        val nameForExport = preferredDisplayName?.trim()?.ifBlank { null } ?: sourceFile.name
        val destName = buildAudioExportFileName(nameForExport, sourceFile)
        return if (PublicMoaMediaStoreExport.exportAudio(appCtx, sourceFile, destName)) {
            destName
        } else {
            null
        }
    }

    private fun guessDocumentMime(ext: String): String =
        when (ext.lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }

    /**
     * PDF 외 문서를 공용 **`Documents/MOA/`** 에 저장한다.
     * @return 저장된 파일명, 실패 시 `null`
     */
    fun exportGenericDocumentFile(
        context: Context,
        sourceFile: File,
        preferredDisplayName: String?,
    ): String? {
        val appCtx = context.applicationContext
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportGenericDocument: invalid source: ${sourceFile.absolutePath}")
            return null
        }
        val raw = preferredDisplayName?.trim()?.ifBlank { null } ?: sourceFile.name
        val destName = sanitizeFileName(raw).ifBlank { sourceFile.name }
        val mime = guessDocumentMime(File(destName).extension)
        return if (PublicMoaMediaStoreExport.exportDocumentToDocumentsMoa(appCtx, sourceFile, destName, mime)) {
            destName
        } else {
            null
        }
    }
}

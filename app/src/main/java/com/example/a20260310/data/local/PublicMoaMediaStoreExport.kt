package com.example.a20260310.data.local

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 사용자가 파일 관리자·갤러리 등에서 찾기 쉬운 **공용 내장 저장소**에 저장한다.
 *
 * - 녹음: `Recordings/MOA/`
 * - 사진(JPEG/PNG 등): `Pictures/MOA/`
 * - 스캔 PDF: 공용 문서 **`Documents/MOA/`** ([MediaStore.Files])
 *
 * Android 10(API 29) 이상은 [MediaStore] + [IS_PENDING] 패턴,
 * 그 이하는 [WRITE_EXTERNAL_STORAGE]가 있을 때 직접 파일 경로에 복사한다.
 */
object PublicMoaMediaStoreExport {

    private const val TAG = "PublicMoaMediaStoreExport"

    /** MediaStore [MediaStore.MediaColumns.RELATIVE_PATH] (끝에 `/` 포함) */
    val relativePathPicturesMoa: String
        get() = "${Environment.DIRECTORY_PICTURES}/MOA/"

    val relativePathRecordingsMoa: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "${Environment.DIRECTORY_RECORDINGS}/MOA/"
            } else {
                "Recordings/MOA/"
            }

    /** 공용 문서 폴더 `Documents/MOA/` */
    val relativePathDocumentsMoa: String
        get() = "${Environment.DIRECTORY_DOCUMENTS}/MOA/"

    fun exportAudio(context: Context, sourceFile: File, displayFileName: String): Boolean {
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportAudio: invalid source ${sourceFile.absolutePath}")
            return false
        }
        val mime = guessAudioMime(sourceFile.extension)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportAudioMediaStoreQ(context, sourceFile, displayFileName, mime)
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), "Recordings${File.separator}MOA")
            legacyCopyToDir(context, sourceFile, displayFileName, dir)
        }
    }

    fun exportImage(context: Context, sourceFile: File, displayFileName: String): Boolean {
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportImage: invalid source ${sourceFile.absolutePath}")
            return false
        }
        val mime = guessImageMime(sourceFile.extension)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (
                insertStreamFinish(
                    context = context,
                    collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    displayName = displayFileName,
                    mimeType = mime,
                    relativePath = relativePathPicturesMoa,
                    sourceFile = sourceFile,
                    filesMediaType = null,
                )
            ) {
                true
            } else {
                Log.w(TAG, "exportImage: Images.Media failed, retrying with Files + Pictures/MOA")
                insertStreamFinish(
                    context = context,
                    collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    displayName = displayFileName,
                    mimeType = mime,
                    relativePath = relativePathPicturesMoa,
                    sourceFile = sourceFile,
                    filesMediaType =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                        } else {
                            null
                        },
                )
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MOA")
            legacyCopyToDir(context, sourceFile, displayFileName, dir)
        }
    }

    /**
     * 스캔 PDF → 공용 **`Documents/MOA/`** ([MediaStore.Files]).
     * `Pictures/`에는 PDF를 넣을 수 없어 문서 공용 경로에 저장한다.
     */
    fun exportPdfToDocumentsMoa(context: Context, sourceFile: File, displayFileName: String): Boolean {
        if (!sourceFile.isFile || !sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "exportPdf: invalid source ${sourceFile.absolutePath}")
            return false
        }
        val docType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT
            } else {
                null
            }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertStreamFinish(
                context = context,
                collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                displayName = displayFileName,
                mimeType = "application/pdf",
                relativePath = relativePathDocumentsMoa,
                sourceFile = sourceFile,
                filesMediaType = docType,
            )
        } else {
            val dir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "MOA",
                )
            legacyCopyToDir(context, sourceFile, displayFileName, dir)
        }
    }

    private fun exportAudioMediaStoreQ(
        context: Context,
        sourceFile: File,
        displayFileName: String,
        mime: String,
    ): Boolean {
        val rel = relativePathRecordingsMoa
        if (
            insertStreamFinish(
                context,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                displayFileName,
                mime,
                rel,
                sourceFile,
                null,
            )
        ) {
            return true
        }
        Log.w(TAG, "Audio.Media insert failed, retrying with Files collection")
        return insertStreamFinish(
            context = context,
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            displayName = displayFileName,
            mimeType = mime,
            relativePath = rel,
            sourceFile = sourceFile,
            filesMediaType = MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO,
        )
    }

    private fun insertStreamFinish(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        sourceFile: File,
        filesMediaType: Int?,
    ): Boolean {
        val resolver = context.contentResolver
        val path = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (filesMediaType != null) {
                    put(MediaStore.Files.FileColumns.MEDIA_TYPE, filesMediaType)
                }
            }
        val uri =
            try {
                resolver.insert(collection, values)
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore insert failed: $displayName -> $path", e)
                null
            } ?: run {
                Log.e(TAG, "MediaStore insert returned null: $displayName -> $path")
                return false
            }
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: run {
                Log.e(TAG, "openOutputStream null for $uri")
                resolver.delete(uri, null, null)
                return false
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            Log.i(TAG, "Saved to public storage: $path$displayName (uri=$uri)")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Write failed for $displayName", e)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Write failed for $displayName", e)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun legacyCopyToDir(context: Context, sourceFile: File, displayFileName: String, destDir: File): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "WRITE_EXTERNAL_STORAGE not granted; cannot write to ${destDir.absolutePath}")
            return false
        }
        return try {
            if (!destDir.exists() && !destDir.mkdirs()) {
                Log.e(TAG, "mkdirs failed: ${destDir.absolutePath}")
                return false
            }
            val dest = File(destDir, displayFileName)
            sourceFile.copyTo(dest, overwrite = true)
            Log.i(TAG, "Saved (legacy): ${dest.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "legacy copy failed", e)
            false
        }
    }

    private fun guessAudioMime(ext: String): String =
        when (ext.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "amr" -> "audio/amr"
            "3gp" -> "audio/3gpp"
            else -> "audio/*"
        }

    private fun guessImageMime(ext: String): String =
        when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
}

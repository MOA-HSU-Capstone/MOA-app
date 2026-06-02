package com.example.a20260310.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.a20260310.data.model.MeetingFileRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 회의 생성·첨부 플로우에서 저장하는 로컬 파일 목록 (SharedPreferences + Gson).
 *
 * - 제목 기준(초안 단계): `"${title}_files_json"`
 * - 백엔드 회의 ID 확정 후: `"meeting_${id}_files_json"` (상세 화면은 이 키만 사용해 동일 제목 다른 회의와 섞이지 않게 함)
 */
object MeetingLocalFilesPrefs {
    const val PREFS_NAME = "moa_prefs"

    fun titleKey(meetingTitle: String): String = "${meetingTitle.trim()}_files_json"

    fun meetingIdKey(meetingId: Int): String? =
        if (meetingId > 0) "meeting_${meetingId}_files_json" else null

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readList(prefs: SharedPreferences, gson: Gson, key: String): List<MeetingFileRow> {
        val json = prefs.getString(key, null) ?: return emptyList()
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MeetingFileRow>>() {}.type
            gson.fromJson<List<MeetingFileRow>>(json, type)
                ?.filter { it.localPath.isNotBlank() }
                .orEmpty()
        } catch (_: Exception) {
            prefs.edit().remove(key).apply()
            emptyList()
        }
    }

    fun writeList(prefs: SharedPreferences, gson: Gson, key: String, list: List<MeetingFileRow>) {
        if (list.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, gson.toJson(list)).apply()
        }
    }

    private fun keysForPersistence(meetingTitle: String, backendMeetingId: Int?): List<String> {
        val out = ArrayList<String>(2)
        out.add(titleKey(meetingTitle))
        meetingIdKey(backendMeetingId ?: 0)?.let { out.add(it) }
        return out.distinct()
    }

    /**
     * 제목 키와(백엔드 ID가 있으면) 회의 ID 키 모두에 동일 목록을 반영한다.
     */
    fun appendOrUpdate(
        context: Context,
        gson: Gson,
        meetingTitle: String,
        backendMeetingId: Int?,
        newItem: MeetingFileRow,
    ) {
        val prefs = prefs(context)
        val keys = keysForPersistence(meetingTitle, backendMeetingId)
        val combined =
            keys
                .flatMap { readList(prefs, gson, it) }
                .distinctBy { it.localPath }
                .filterNot { it.localPath == newItem.localPath }
                .plus(newItem)
        for (key in keys) {
            writeList(prefs, gson, key, combined)
        }
    }

    fun removeByLocalPath(
        context: Context,
        gson: Gson,
        meetingTitle: String,
        backendMeetingId: Int?,
        localPath: String,
    ) {
        val prefs = prefs(context)
        for (key in keysForPersistence(meetingTitle, backendMeetingId)) {
            val cur = readList(prefs, gson, key).filterNot { it.localPath == localPath }
            writeList(prefs, gson, key, cur)
        }
    }

    /** ID 키가 비어 있을 때만, 제목 키 목록을 ID 키로 한 번 복사 (이전 버전 호환). */
    fun copyTitleKeyToMeetingIdIfEmpty(context: Context, gson: Gson, meetingTitle: String, meetingId: Int) {
        if (meetingId <= 0) return
        val prefs = prefs(context)
        val idKey = meetingIdKey(meetingId) ?: return
        if (readList(prefs, gson, idKey).isNotEmpty()) return
        val fromTitle = readList(prefs, gson, titleKey(meetingTitle))
        if (fromTitle.isEmpty()) return
        writeList(prefs, gson, idKey, fromTitle)
    }

    fun meetingFolderKey(meetingId: Int): String? =
        if (meetingId > 0) "meeting_${meetingId}_folder" else null

    fun saveMeetingFolder(context: Context, meetingId: Int, folderName: String) {
        val key = meetingFolderKey(meetingId) ?: return
        prefs(context).edit()
            .putString(key, folderName.trim())
            .apply()
    }

    fun getMeetingFolder(context: Context, meetingId: Int): String? {
        val key = meetingFolderKey(meetingId) ?: return null
        return prefs(context).getString(key, null)
    }
}

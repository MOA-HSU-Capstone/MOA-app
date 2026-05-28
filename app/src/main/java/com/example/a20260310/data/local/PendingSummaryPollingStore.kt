package com.example.a20260310.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PendingSummaryPollJob(
    val meetingId: Int,
    val meetingTitle: String,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
)

data class CompletedSummaryPoll(
    val meetingId: Int,
    val meetingTitle: String,
    val completedAtMs: Long = System.currentTimeMillis(),
)

data class FailedSummaryPoll(
    val meetingId: Int,
    val meetingTitle: String,
    val errorMessage: String,
    val failedAtMs: Long = System.currentTimeMillis(),
)

/**
 * 백그라운드 요약 폴링 상태 (WorkManager ↔ UI 동기화).
 */
object PendingSummaryPollingStore {
    private const val KEY_PENDING = "pending_summary_poll_jobs_json"
    private const val KEY_COMPLETED = "completed_summary_poll_json"
    private const val KEY_FAILED = "failed_summary_poll_json"

    private val gson = Gson()

    fun addPending(context: Context, job: PendingSummaryPollJob) {
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val list =
            readPending(prefs)
                .filterNot { it.meetingId == job.meetingId }
                .plus(job)
        prefs.edit().putString(KEY_PENDING, gson.toJson(list)).apply()
    }

    fun removePending(context: Context, meetingId: Int) {
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val list = readPending(prefs).filterNot { it.meetingId == meetingId }
        if (list.isEmpty()) {
            prefs.edit().remove(KEY_PENDING).apply()
        } else {
            prefs.edit().putString(KEY_PENDING, gson.toJson(list)).apply()
        }
    }

    fun readPending(context: Context): List<PendingSummaryPollJob> =
        readPending(MeetingLocalFilesPrefs.prefs(context))

    private fun readPending(prefs: android.content.SharedPreferences): List<PendingSummaryPollJob> {
        val json = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingSummaryPollJob>>() {}.type
            gson.fromJson<List<PendingSummaryPollJob>>(json, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun markCompleted(context: Context, meetingId: Int, meetingTitle: String) {
        removePending(context, meetingId)
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val list =
            readCompleted(prefs)
                .filterNot { it.meetingId == meetingId }
                .plus(CompletedSummaryPoll(meetingId = meetingId, meetingTitle = meetingTitle))
        prefs.edit().putString(KEY_COMPLETED, gson.toJson(list)).apply()
    }

    fun consumeCompleted(context: Context): List<CompletedSummaryPoll> {
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val list = readCompleted(prefs)
        if (list.isNotEmpty()) {
            prefs.edit().remove(KEY_COMPLETED).apply()
        }
        return list
    }

    fun markFailed(context: Context, meetingId: Int, meetingTitle: String, errorMessage: String) {
        removePending(context, meetingId)
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val msg = errorMessage.trim().ifBlank { "요약 생성에 실패했습니다." }
        val list =
            readFailed(prefs)
                .filterNot { it.meetingId == meetingId }
                .plus(
                    FailedSummaryPoll(
                        meetingId = meetingId,
                        meetingTitle = meetingTitle,
                        errorMessage = msg,
                    ),
                )
        prefs.edit().putString(KEY_FAILED, gson.toJson(list)).apply()
    }

    fun consumeFailed(context: Context): List<FailedSummaryPoll> {
        val prefs = MeetingLocalFilesPrefs.prefs(context)
        val list = readFailed(prefs)
        if (list.isNotEmpty()) {
            prefs.edit().remove(KEY_FAILED).apply()
        }
        return list
    }

    private fun readCompleted(prefs: android.content.SharedPreferences): List<CompletedSummaryPoll> {
        val json = prefs.getString(KEY_COMPLETED, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CompletedSummaryPoll>>() {}.type
            gson.fromJson<List<CompletedSummaryPoll>>(json, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readFailed(prefs: android.content.SharedPreferences): List<FailedSummaryPoll> {
        val json = prefs.getString(KEY_FAILED, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FailedSummaryPoll>>() {}.type
            gson.fromJson<List<FailedSummaryPoll>>(json, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

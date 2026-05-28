package com.example.a20260310.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.a20260310.data.local.PendingSummaryPollJob
import com.example.a20260310.data.local.PendingSummaryPollingStore
import java.util.concurrent.TimeUnit
import kotlin.math.min

object SummaryPollScheduler {
    const val KEY_MEETING_ID = "meeting_id"
    const val KEY_MEETING_TITLE = "meeting_title"
    const val KEY_ATTEMPT = "attempt"

    private fun uniqueWorkName(meetingId: Int): String = "summary_poll_meeting_$meetingId"

    fun scheduleInitial(context: Context, meetingId: Int, meetingTitle: String) {
        val appContext = context.applicationContext
        PendingSummaryPollingStore.addPending(
            appContext,
            PendingSummaryPollJob(meetingId = meetingId, meetingTitle = meetingTitle),
        )
        enqueue(appContext, meetingId, meetingTitle, attempt = 0)
    }

    fun enqueue(
        context: Context,
        meetingId: Int,
        meetingTitle: String,
        attempt: Int,
    ) {
        val delaySeconds = delaySecondsForAttempt(attempt)
        val request =
            OneTimeWorkRequestBuilder<SummaryPollWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_MEETING_ID to meetingId,
                        KEY_MEETING_TITLE to meetingTitle,
                        KEY_ATTEMPT to attempt,
                    ),
                )
                .addTag(SummaryPollWorker.TAG)
                .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                uniqueWorkName(meetingId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }

    fun cancel(context: Context, meetingId: Int) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueWorkName(meetingId))
    }

    fun delaySecondsForAttempt(attempt: Int): Long {
        val steps = longArrayOf(15L, 30L, 45L, 60L)
        return steps[min(attempt, steps.lastIndex)]
    }
}

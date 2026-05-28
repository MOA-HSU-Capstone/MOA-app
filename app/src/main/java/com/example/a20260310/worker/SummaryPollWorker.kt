package com.example.a20260310.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.a20260310.data.local.PendingSummaryPollingStore
import com.example.a20260310.data.poll.SummaryPoller
import com.example.a20260310.data.remote.ApiErrorParser
import com.example.a20260310.data.repository.MeetingRepository
import retrofit2.HttpException

/**
 * 요약 생성 완료까지 [getSummary]를 반복 조회한다.
 * 404/409·일시적 네트워크 타임아웃은 재시도, 그 외 HTTP 오류는 즉시 실패.
 */
class SummaryPollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val meetingId = inputData.getInt(SummaryPollScheduler.KEY_MEETING_ID, -1)
        val meetingTitle =
            inputData.getString(SummaryPollScheduler.KEY_MEETING_TITLE)?.trim().orEmpty()
        val attempt = inputData.getInt(SummaryPollScheduler.KEY_ATTEMPT, 0)

        if (meetingId <= 0) {
            return Result.failure()
        }

        val title = meetingTitle.ifBlank { "회의" }
        val repository = MeetingRepository()
        return try {
            repository.getSummary(meetingId)
            Log.d(TAG, "background getSummary success meetingId=$meetingId")
            PendingSummaryPollingStore.markCompleted(applicationContext, meetingId, title)
            SummaryPollScheduler.cancel(applicationContext, meetingId)
            Result.success()
        } catch (e: HttpException) {
            if (SummaryPoller.isNotReady(e)) {
                scheduleNext(meetingId, title, attempt, "not-ready code=${e.code()}")
                Result.success()
            } else {
                val message =
                    ApiErrorParser.httpMessage(
                        error = e,
                        fallback = "요약 생성에 실패했습니다.",
                        includeCode = true,
                    )
                Log.e(TAG, "background getSummary failed meetingId=$meetingId code=${e.code()}", e)
                failAndStop(meetingId, title, message)
                Result.failure()
            }
        } catch (e: Exception) {
            if (SummaryPoller.isTransientNetworkError(e)) {
                scheduleNext(meetingId, title, attempt, "transient network")
                Result.success()
            } else {
                val message = e.message?.takeIf { it.isNotBlank() } ?: "요약 생성에 실패했습니다."
                Log.e(TAG, "background getSummary error meetingId=$meetingId", e)
                failAndStop(meetingId, title, message)
                Result.failure()
            }
        }
    }

    private fun scheduleNext(meetingId: Int, title: String, attempt: Int, reason: String) {
        Log.d(TAG, "background getSummary retry meetingId=$meetingId reason=$reason attempt=$attempt")
        SummaryPollScheduler.enqueue(
            applicationContext,
            meetingId,
            title,
            attempt = attempt + 1,
        )
    }

    private fun failAndStop(meetingId: Int, meetingTitle: String, message: String) {
        PendingSummaryPollingStore.markFailed(applicationContext, meetingId, meetingTitle, message)
        SummaryPollScheduler.cancel(applicationContext, meetingId)
    }

    companion object {
        const val TAG = "SummaryPollWorker"
    }
}

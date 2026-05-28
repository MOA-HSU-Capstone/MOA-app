package com.example.a20260310.data.poll

import android.os.SystemClock
import android.util.Log
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.data.repository.MeetingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.math.min

object SummaryPoller {
    private const val TAG = "SummaryPoller"

    /** 아직 생성 중 — 폴링 계속 */
    fun isNotReady(error: HttpException): Boolean = error.code() == 404 || error.code() == 409

    /**
     * 서버는 STT 처리 중인데 앱 소켓만 끊긴 경우. 실패가 아니라 계속 대기/백그라운드 폴링.
     */
    fun isTransientNetworkError(error: Throwable): Boolean {
        if (error is InterruptedIOException || error is SocketTimeoutException) return true
        if (error is IOException && error !is HttpException) {
            val msg = error.message?.lowercase().orEmpty()
            if (msg.contains("timeout") || msg.contains("timed out")) return true
        }
        return false
    }

    suspend fun <T> retryOnTransientNetwork(
        maxAttempts: Int = 2,
        delayMs: Long = 3_000L,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend () -> T,
    ): T {
        var last: Throwable? = null
        repeat(maxAttempts) { index ->
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!isTransientNetworkError(e)) throw e
                last = e
                onRetry?.invoke(index + 1, e)
                if (index < maxAttempts - 1) delay(delayMs)
            }
        }
        throw last ?: IllegalStateException("retryOnTransientNetwork exhausted")
    }

    suspend fun pollUntilReady(
        repository: MeetingRepository,
        meetingId: Int,
        maxWaitMs: Long?,
        pollIntervalMs: Long = 4_000L,
        onNotReady: ((attempt: Int, elapsedMs: Long) -> Unit)? = null,
    ): SummaryDetailResponseDto {
        val startedAt = SystemClock.elapsedRealtime()
        var attempt = 0
        while (true) {
            attempt += 1
            try {
                return repository.getSummary(meetingId)
            } catch (e: HttpException) {
                if (!isNotReady(e)) throw e
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                onNotReady?.invoke(attempt, elapsed)
                if (maxWaitMs != null && elapsed >= maxWaitMs) {
                    Log.w(
                        TAG,
                        "summary not ready within ui limit meetingId=$meetingId elapsedMs=$elapsed maxWaitMs=$maxWaitMs",
                    )
                    throw SummaryNotReadyException(meetingId, elapsed)
                }
                delay(pollDelayMs(maxWaitMs, startedAt, pollIntervalMs))
            } catch (e: Throwable) {
                if (!isTransientNetworkError(e)) throw e
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                onNotReady?.invoke(attempt, elapsed)
                Log.w(
                    TAG,
                    "getSummary transient network meetingId=$meetingId attempt=$attempt elapsedMs=$elapsed",
                    e,
                )
                if (maxWaitMs != null && elapsed >= maxWaitMs) {
                    throw SummaryNotReadyException(meetingId, elapsed)
                }
                delay(pollDelayMs(maxWaitMs, startedAt, pollIntervalMs))
            }
        }
    }

    private fun pollDelayMs(
        maxWaitMs: Long?,
        startedAt: Long,
        pollIntervalMs: Long,
    ): Long {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = maxWaitMs?.let { (it - elapsed).coerceAtLeast(0L) }
        return if (remaining != null) {
            min(pollIntervalMs, remaining).coerceAtLeast(1L)
        } else {
            pollIntervalMs
        }
    }
}

class SummaryNotReadyException(
    val meetingId: Int,
    val elapsedMs: Long,
) : Exception("Summary not ready for meeting $meetingId after ${elapsedMs}ms")

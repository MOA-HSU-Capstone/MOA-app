package com.example.a20260310.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a20260310.data.remote.dto.UploadedFileDto
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.data.remote.dto.SummaryUpdateRequest
import com.example.a20260310.data.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class MeetingDetailUiState(
    val meeting: MeetingResponseDto? = null,
    val summary: SummaryDetailResponseDto? = null,
    val isLoading: Boolean = false,
)

sealed interface MeetingDetailEffect {
    data class Toast(val message: String) : MeetingDetailEffect
    data class Error(val message: String) : MeetingDetailEffect
    data object NavigateToLogin : MeetingDetailEffect
    data object MeetingDeleted : MeetingDetailEffect
}

class DetailViewModel(
    private val meetingId: Int,
    private val repository: MeetingRepository = MeetingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeetingDetailUiState())
    val uiState: StateFlow<MeetingDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MeetingDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // DetailViewModel.kt

    private val _meetingFiles = MutableStateFlow<List<UploadedFileDto>>(emptyList())
    val meetingFiles: StateFlow<List<UploadedFileDto>> = _meetingFiles

    private fun emitFailure(e: Throwable) {
        viewModelScope.launch {
            if (e is HttpException && e.code() == 401) {
                _effects.send(MeetingDetailEffect.NavigateToLogin)
                return@launch
            }
            val msg =
                when (e) {
                    is IllegalArgumentException -> e.message?.takeIf { it.isNotBlank() }
                        ?: "잘못된 요청입니다."

                    is HttpException -> "요청에 실패했습니다. (${e.code()})"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "오류가 발생했습니다."
                }
            _effects.send(MeetingDetailEffect.Error(msg))
        }
    }

    private fun guardMeetingId(): Boolean {
        if (meetingId <= 0) {
            viewModelScope.launch {
                _effects.send(
                    MeetingDetailEffect.Toast("유효한 회의가 아니면 서버에 저장하거나 불러올 수 없습니다."),
                )
            }
            return false
        }
        return true
    }

    fun loadInitial() {
        if (meetingId <= 0) {
            viewModelScope.launch {
                _effects.send(
                    MeetingDetailEffect.Toast("유효한 회의가 아니면 서버 연동 기능을 사용할 수 없습니다."),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val meeting = repository.getMeeting(meetingId)
                    val summary = repository.getSummary(meetingId)
                    meeting to summary
                }
            }.onSuccess { (m, s) ->
                _uiState.value = MeetingDetailUiState(meeting = m, summary = s, isLoading = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun refreshMeetingHeader() {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.getMeeting(meetingId) }
            }.onSuccess { m ->
                _uiState.value = _uiState.value.copy(meeting = m)
            }.onFailure { emitFailure(it) }
        }
    }

    fun refreshSummary() {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.getSummary(meetingId) }
            }.onSuccess { s ->
                _uiState.value = _uiState.value.copy(summary = s)
            }.onFailure { emitFailure(it) }
        }
    }

    fun updateMeetingInfo(
        title: String,
        meetingDate: String,
        meetingTime: String,
        attendeesComma: String,
    ) {
        if (!guardMeetingId()) return
        val attendees =
            attendeesComma.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.updateMeeting(meetingId, title, meetingDate, meetingTime, attendees)
                    repository.getMeeting(meetingId)
                }
            }.onSuccess { m ->
                _uiState.value = _uiState.value.copy(meeting = m, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("회의 정보가 저장되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun deleteMeeting() {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) { repository.deleteMeeting(meetingId) }
            }.onSuccess { resp ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (resp.isSuccessful) {
                    _effects.send(MeetingDetailEffect.MeetingDeleted)
                } else {
                    _effects.send(MeetingDetailEffect.Error("회의 삭제에 실패했습니다."))
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun patchSummaryBody(text: String) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.updateSummary(meetingId, SummaryUpdateRequest(summary = text))
                    repository.getSummary(meetingId)
                }
            }.onSuccess { detail ->
                _uiState.value = _uiState.value.copy(summary = detail, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("수정되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun addDecision(content: String) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.createDecision(meetingId, content)
                    repository.getSummary(meetingId)
                }
            }.onSuccess { s ->
                _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("추가되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun updateDecision(decisionId: Int, content: String) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            if (decisionId <= 0) {
                resyncSummaryAfterStaleRow()
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.updateDecision(decisionId, content)
                    repository.getSummary(meetingId)
                }
            }.onSuccess { s ->
                _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("수정되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun deleteDecision(decisionId: Int) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            if (decisionId <= 0) {
                resyncSummaryAfterStaleRow()
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.deleteDecision(decisionId)
                }
            }.onSuccess { resp ->
                if (resp.isSuccessful) {
                    runCatching {
                        withContext(Dispatchers.IO) { repository.getSummary(meetingId) }
                    }.onSuccess { s ->
                        _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                        _effects.send(MeetingDetailEffect.Toast("삭제되었습니다."))
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        emitFailure(e)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _effects.send(MeetingDetailEffect.Error("삭제에 실패했습니다."))
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun addActionItem(task: String, assignee: String?, dueDate: String?) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.createActionItem(meetingId, task, assignee, dueDate)
                    repository.getSummary(meetingId)
                }
            }.onSuccess { s ->
                _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("추가되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun updateActionItem(
        actionItemId: Int,
        task: String,
        assignee: String?,
        dueDate: String?,
    ) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            if (actionItemId <= 0) {
                resyncSummaryAfterStaleRow()
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.updateActionItem(actionItemId, task, assignee, dueDate)
                    repository.getSummary(meetingId)
                }
            }.onSuccess { s ->
                _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                _effects.send(MeetingDetailEffect.Toast("수정되었습니다."))
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    fun deleteActionItem(actionItemId: Int) {
        if (!guardMeetingId()) return
        viewModelScope.launch {
            if (actionItemId <= 0) {
                resyncSummaryAfterStaleRow()
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) { repository.deleteActionItem(actionItemId) }
            }.onSuccess { resp ->
                if (resp.isSuccessful) {
                    runCatching {
                        withContext(Dispatchers.IO) { repository.getSummary(meetingId) }
                    }.onSuccess { s ->
                        _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
                        _effects.send(MeetingDetailEffect.Toast("삭제되었습니다."))
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        emitFailure(e)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _effects.send(MeetingDetailEffect.Error("삭제에 실패했습니다."))
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                emitFailure(e)
            }
        }
    }

    private suspend fun resyncSummaryAfterStaleRow() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        runCatching {
            withContext(Dispatchers.IO) { repository.getSummary(meetingId) }
        }.onSuccess { s ->
            _uiState.value = _uiState.value.copy(summary = s, isLoading = false)
            _effects.send(MeetingDetailEffect.Toast("목록을 서버 기준으로 갱신했습니다."))
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(isLoading = false)
            emitFailure(e)
        }
    }

    suspend fun getMeetingFiles(meetingId: Int): List<UploadedFileDto> {
        return repository.getMeetingFiles(meetingId)
    }

}

class DetailViewModelFactory(
    private val meetingId: Int,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            "unknown ViewModel ${modelClass.name}"
        }
        return DetailViewModel(meetingId) as T
    }

}

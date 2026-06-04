package com.example.a20260310.ui.add

import android.os.Bundle
import android.view.View
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.model.MeetingDraft
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class MeetingCreateFragment : Fragment(R.layout.fragment_meeting_create) {

    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    private val participants = mutableListOf<String>()
    private lateinit var selectedDateStr: String

    private val datePattern = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 새 인스턴스로 회의 정보 입력 화면에 들어올 때만 호출됨(AddMethod→뒤로 시 재진입 시에는 호출되지 않음 → 첨부 유지)
        sessionViewModel.clearNewMeetingAttachmentSelection()

        val nameLayout = view.findViewById<TextInputLayout>(R.id.nameLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        val dateLayout = view.findViewById<TextInputLayout>(R.id.dateLayout)
        val dateInput = view.findViewById<TextInputEditText>(R.id.dateInput)
        val timeLayout = view.findViewById<TextInputLayout>(R.id.timeLayout)
        val timeInput = view.findViewById<TextInputEditText>(R.id.timeInput)
        val participantInput = view.findViewById<TextInputEditText>(R.id.participantInput)
        val participantChipGroup = view.findViewById<ChipGroup>(R.id.participantChipGroup)

        var selectedDateMillis = MaterialDatePicker.todayInUtcMilliseconds()
        selectedDateStr = formatDisplayDate(selectedDateMillis)
        dateInput.setText(selectedDateStr)
        nameLayout.prefixText = "$selectedDateStr "

        val nowCal = java.util.Calendar.getInstance()
        var hour = nowCal.get(java.util.Calendar.HOUR_OF_DAY)
        var minute = nowCal.get(java.util.Calendar.MINUTE)
        timeInput.setText(formatTime(hour, minute))

        fun openDatePicker() {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selectedDateMillis)
                .setTitleText("회의 날짜")
                .build()
            picker.addOnPositiveButtonClickListener { ms ->
                selectedDateMillis = ms
                selectedDateStr = formatDisplayDate(ms)
                dateInput.setText(selectedDateStr)
                nameLayout.prefixText = "$selectedDateStr "
            }
            picker.show(childFragmentManager, "meeting_date")
        }

        fun openTimePicker() {
            val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker_spinner, null)
            val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker).apply {
                setIs24HourView(false)
                this.hour = hour
                this.minute = minute
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("회의 시간")
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    hour = timePicker.hour
                    minute = timePicker.minute
                    timeInput.setText(formatTime(hour, minute))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        listOf(dateLayout, dateInput).forEach { v ->
            v.setOnClickListener { openDatePicker() }
        }
        listOf(timeLayout, timeInput).forEach { v ->
            v.setOnClickListener { openTimePicker() }
        }

        view.findViewById<MaterialButton>(R.id.addParticipantButton).setOnClickListener {
            val name = participantInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            participants.add(name)
            participantChipGroup.addView(createParticipantChip(participantChipGroup, name))
            participantInput.text = null
        }

        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {
            val body = nameInput.text?.toString()?.trim().orEmpty()
            val fullTitle = buildFullTitle(body)

            val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
            prefs.edit().putString("current_meeting_name", fullTitle).apply()
            prefs.edit()
                .putString(
                    "${fullTitle}_participants",
                    participants.joinToString(", ")
                )
                .apply()

            val folderId = sessionViewModel.selectedFolderId.value
            val folderName = sessionViewModel.selectedFolderName.value

            sessionViewModel.setDraft(
                MeetingDraft(
                    title = fullTitle,
                    date = dateInput.text?.toString()?.trim().orEmpty(),
                    time = timeInput.text?.toString()?.trim().orEmpty(),
                    attendees = participants.joinToString(", "),
                    folderId = sessionViewModel.selectedFolderId.value,
                    folderName = sessionViewModel.selectedFolderName.value,
                ),
            )

            findNavController().navigate(R.id.action_meetingCreateFragment_to_addMethodFragment)
        }
    }

    private fun formatDisplayDate(utcMillis: Long): String =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(datePattern)

    /** [h]는 0–23, 표시는 한국어 오전/오후 + 12시간제 */
    private fun formatTime(h: Int, m: Int): String {
        val cal =
            Calendar.getInstance(Locale.KOREA).apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return SimpleDateFormat("a h:mm", Locale.KOREA).format(cal.time)
    }

    /**
     * 제목 입력란의 prefix로 보이는 회의 날짜와, 사용자가 입력한 본문을 합쳐 실제 저장용 제목으로 만든다.
     * (prefixText는 EditText 값에 포함되지 않으므로 여기서 반드시 붙인다.)
     */
    private fun buildFullTitle(body: String): String {
        val datePart = selectedDateStr.trim()
        val namePart = body.trim()
        return when {
            datePart.isEmpty() -> namePart
            namePart.isEmpty() -> datePart
            else -> "$datePart $namePart"
        }
    }

    private fun createParticipantChip(group: ChipGroup, label: String): Chip {
        val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
            text = label
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                val idx = group.indexOfChild(this)
                group.removeView(this)
                if (idx >= 0 && idx < participants.size) participants.removeAt(idx)
            }
        }
        return chip
    }
}

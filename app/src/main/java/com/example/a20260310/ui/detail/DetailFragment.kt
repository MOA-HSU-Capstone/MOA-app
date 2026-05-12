package com.example.a20260310.ui.detail

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.ActionItem
import com.example.a20260310.data.model.MeetingSummary
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.data.model.toDomain
import com.example.a20260310.data.model.toDto
import com.example.a20260310.data.remote.RetrofitClient
import com.example.a20260310.data.remote.dto.SummaryUpdateRequestDto
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true
    private var meetingSummary: MeetingSummary? = null

    private lateinit var titleView: TextView
    private lateinit var summaryBtn: MaterialButton
    private lateinit var fileBtn: MaterialButton
    private lateinit var fileRecycler: RecyclerView
    private lateinit var summaryScroll: ScrollView

    private lateinit var summaryText: TextView
    private lateinit var decisionText1: TextView
    private lateinit var decisionText2: TextView

    private lateinit var taskTitle1: TextView
    private lateinit var taskOwner1: TextView
    private lateinit var taskDeadline1: TextView

    private lateinit var taskTitle2: TextView
    private lateinit var taskOwner2: TextView
    private lateinit var taskDeadline2: TextView

    private lateinit var taskTitle3: TextView
    private lateinit var taskOwner3: TextView
    private lateinit var taskDeadline3: TextView

    private val meetingId: Int
        get() = requireArguments().getInt("meetingId")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleView = view.findViewById(R.id.title)
        summaryBtn = view.findViewById(R.id.tabSummary)
        fileBtn = view.findViewById(R.id.tabFiles)
        fileRecycler = view.findViewById(R.id.fileRecycler)
        summaryScroll = view.findViewById(R.id.summaryScroll)

        summaryText = view.findViewById(R.id.summaryText)
        decisionText1 = view.findViewById(R.id.decisionText1)
        decisionText2 = view.findViewById(R.id.decisionText2)

        taskTitle1 = view.findViewById(R.id.taskTitle1)
        taskOwner1 = view.findViewById(R.id.taskOwner1)
        taskDeadline1 = view.findViewById(R.id.taskDeadline1)

        taskTitle2 = view.findViewById(R.id.taskTitle2)
        taskOwner2 = view.findViewById(R.id.taskOwner2)
        taskDeadline2 = view.findViewById(R.id.taskDeadline2)

        taskTitle3 = view.findViewById(R.id.taskTitle3)
        taskOwner3 = view.findViewById(R.id.taskOwner3)
        taskDeadline3 = view.findViewById(R.id.taskDeadline3)

        titleView.text = arguments?.getString("meetingTitle") ?: "회의"
        fileRecycler.layoutManager = LinearLayoutManager(requireContext())

        bindEmptySummary()
        setupListeners(view)
        updateTabs(summaryBtn, fileBtn)
        showSummary(summaryScroll, fileRecycler)
        fetchSummary()
    }

    private fun setupListeners(view: View) {
        summaryBtn.setOnClickListener {
            isSummaryTab = true
            updateTabs(summaryBtn, fileBtn)
            showSummary(summaryScroll, fileRecycler)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            updateTabs(summaryBtn, fileBtn)
            showFiles(summaryScroll, fileRecycler)
        }

        view.findViewById<View>(R.id.btnEditSummary).setOnClickListener {
            showEditSummaryDialog()
        }

        view.findViewById<View>(R.id.btnEditDecision1).setOnClickListener {
            showEditDecisionDialog(0)
        }

        view.findViewById<View>(R.id.btnEditDecision2).setOnClickListener {
            showEditDecisionDialog(1)
        }

        view.findViewById<View>(R.id.btnEditAction1).setOnClickListener {
            showEditActionDialog(0)
        }

        view.findViewById<View>(R.id.btnEditAction2).setOnClickListener {
            showEditActionDialog(1)
        }

        view.findViewById<View>(R.id.btnEditAction3).setOnClickListener {
            showEditActionDialog(2)
        }
    }

    private fun fetchSummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.getMeetingSummary(meetingId)
            }.onSuccess { response ->
                val summary = response.summary.toDomain()
                meetingSummary = summary
                bindSummary(summary)
            }.onFailure {
                Toast.makeText(requireContext(), "회의 요약을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindEmptySummary() {
        summaryText.text = "—"
        decisionText1.text = "—"
        decisionText2.text = "—"

        bindActionRow(taskTitle1, taskOwner1, taskDeadline1, ActionItem(task = "", owner = null, deadline = null))
        bindActionRow(taskTitle2, taskOwner2, taskDeadline2, ActionItem(task = "", owner = null, deadline = null))
        bindActionRow(taskTitle3, taskOwner3, taskDeadline3, ActionItem(task = "", owner = null, deadline = null))
    }

    private fun bindSummary(summary: MeetingSummary) {
        summaryText.text = summary.summary.ifBlank { "—" }

        val decisions = normalizedDecisions(summary)
        decisionText1.text = decisions[0].ifBlank { "—" }
        decisionText2.text = decisions[1].ifBlank { "—" }

        val actions = normalizedActions(summary)
        bindActionRow(taskTitle1, taskOwner1, taskDeadline1, actions[0])
        bindActionRow(taskTitle2, taskOwner2, taskDeadline2, actions[1])
        bindActionRow(taskTitle3, taskOwner3, taskDeadline3, actions[2])
    }

    private fun bindActionRow(
        titleView: TextView,
        ownerView: TextView,
        deadlineView: TextView,
        item: ActionItem,
    ) {
        titleView.text = item.task.ifBlank { "—" }
        ownerView.text = item.owner?.ifBlank { null } ?: "미정"
        deadlineView.text = item.deadline?.ifBlank { null } ?: "미정"
    }

    private fun normalizedDecisions(summary: MeetingSummary): List<String> =
        listOf(
            summary.decisions.getOrNull(0).orEmpty(),
            summary.decisions.getOrNull(1).orEmpty(),
        )

    private fun normalizedActions(summary: MeetingSummary): List<ActionItem> =
        listOf(
            summary.actionItems.getOrNull(0) ?: ActionItem(task = "", owner = null, deadline = null),
            summary.actionItems.getOrNull(1) ?: ActionItem(task = "", owner = null, deadline = null),
            summary.actionItems.getOrNull(2) ?: ActionItem(task = "", owner = null, deadline = null),
        )

    private fun updateDecision(
        current: MeetingSummary,
        index: Int,
        newText: String,
    ): MeetingSummary {
        val decisions = normalizedDecisions(current).toMutableList()
        decisions[index] = newText.trim()
        return current.copy(decisions = decisions)
    }

    private fun updateAction(
        current: MeetingSummary,
        index: Int,
        task: String,
        owner: String,
        deadline: String,
    ): MeetingSummary {
        val actions = normalizedActions(current).toMutableList()
        actions[index] = ActionItem(
            task = task.trim(),
            owner = owner.trim().ifBlank { null },
            deadline = deadline.trim().ifBlank { null },
        )
        return current.copy(actionItems = actions)
    }

    private fun showEditSummaryDialog() {
        val current = meetingSummary ?: return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_summary, null, false)

        val etSummary = dialogView.findViewById<TextInputEditText>(R.id.etSummary)
        etSummary.setText(current.summary)

        AlertDialog.Builder(requireContext())
            .setTitle("회의 요약 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val updated = current.copy(
                    summary = etSummary.text?.toString().orEmpty().trim()
                )
                patchSummary(updated)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditDecisionDialog(index: Int) {
        val current = meetingSummary ?: return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_decision, null, false)

        val etDecision = dialogView.findViewById<TextInputEditText>(R.id.etDecision)
        etDecision.setText(normalizedDecisions(current)[index])

        AlertDialog.Builder(requireContext())
            .setTitle("결정 사항 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val updated = updateDecision(
                    current = current,
                    index = index,
                    newText = etDecision.text?.toString().orEmpty(),
                )
                patchSummary(updated)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditActionDialog(index: Int) {
        val current = meetingSummary ?: return
        val action = normalizedActions(current)[index]

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_action, null, false)

        val etTask = dialogView.findViewById<TextInputEditText>(R.id.etTask)
        val etOwner = dialogView.findViewById<TextInputEditText>(R.id.etOwner)
        val etDeadline = dialogView.findViewById<TextInputEditText>(R.id.etDeadline)

        etTask.setText(action.task)
        etOwner.setText(action.owner.orEmpty())
        etDeadline.setText(action.deadline.orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle("할 일 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val updated = updateAction(
                    current = current,
                    index = index,
                    task = etTask.text?.toString().orEmpty(),
                    owner = etOwner.text?.toString().orEmpty(),
                    deadline = etDeadline.text?.toString().orEmpty(),
                )
                patchSummary(updated)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun patchSummary(updated: MeetingSummary) {
        val request = SummaryUpdateRequestDto(
            summary = updated.toDto()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.updateMeetingSummary(
                    meetingId = meetingId,
                    request = request,
                )
            }.onSuccess { response ->
                val updatedDomain = response.summary.toDomain()
                meetingSummary = updatedDomain
                bindSummary(updatedDomain)
                Toast.makeText(requireContext(), "수정되었습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), "수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTabs(summaryBtn: MaterialButton, fileBtn: MaterialButton) {
        if (isSummaryTab) {
            fileBtn.setBackgroundColor(resources.getColor(R.color.color_on_primary, null))
            fileBtn.setTextColor(resources.getColor(android.R.color.black, null))

            summaryBtn.setBackgroundColor(resources.getColor(R.color.color_primary, null))
            summaryBtn.setTextColor(resources.getColor(R.color.color_on_primary, null))
        } else {
            summaryBtn.setBackgroundColor(resources.getColor(R.color.color_on_primary, null))
            summaryBtn.setTextColor(resources.getColor(android.R.color.black, null))

            fileBtn.setBackgroundColor(resources.getColor(R.color.color_primary, null))
            fileBtn.setTextColor(resources.getColor(R.color.color_on_primary, null))
        }
    }

    private fun showSummary(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.VISIBLE
        recycler.visibility = View.GONE
    }

    private fun showFiles(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        val files = loadFiles()
        recycler.adapter = SimpleRowAdapter(files) {
            Toast.makeText(requireContext(), it.title, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFiles(): List<SimpleRow> {
        return emptyList()
    }
}
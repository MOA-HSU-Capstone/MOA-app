package com.example.a20260310.ui.detail

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.BuildConfig
import com.example.a20260310.R
import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.local.MeetingLocalFilesPrefs
import com.example.a20260310.data.model.DecisionItem
import com.example.a20260310.data.model.DetailTaskItem
import com.example.a20260310.data.model.MeetingFileRow
import com.example.a20260310.data.remote.dto.ActionItemDto
import com.example.a20260310.data.remote.dto.DecisionDto
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.ui.common.WrappingLinearLayoutManager
import com.example.a20260310.viewmodel.DetailViewModel
import com.example.a20260310.viewmodel.DetailViewModelFactory
import com.example.a20260310.viewmodel.MeetingDetailEffect
import com.example.a20260310.viewmodel.MeetingDetailUiState
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private val gson = Gson()

    private var isSummaryTab = true

    private lateinit var detailProgress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var meetingDateView: TextView
    private lateinit var meetingTimeView: TextView
    private lateinit var meetingAttendeesView: TextView
    private lateinit var summaryBtn: MaterialButton
    private lateinit var fileBtn: MaterialButton
    private lateinit var fileRecycler: RecyclerView
    private lateinit var summaryScroll: View
    private lateinit var summaryText: TextView
    private lateinit var fileTabContainer: View
    private lateinit var fileEmptyState: TextView
    private lateinit var emptyDecisionsHint: TextView

    private lateinit var decisionAdapter: DecisionAdapter
    private lateinit var actionAdapter: ActionAdapter

    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    private val meetingId: Int
        get() = arguments?.takeIf { it.containsKey("meetingId") }?.getInt("meetingId", 0) ?: 0

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(meetingId)
    }

    private val meetingTitle: String
        get() = arguments?.getString("meetingTitle") ?: "회의"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detailProgress = view.findViewById(R.id.detailProgress)
        titleView = view.findViewById(R.id.title)
        meetingDateView = view.findViewById(R.id.meetingDate)
        meetingTimeView = view.findViewById(R.id.meetingTime)
        meetingAttendeesView = view.findViewById(R.id.meetingAttendees)
        summaryBtn = view.findViewById(R.id.tabSummary)
        fileBtn = view.findViewById(R.id.tabFiles)
        fileRecycler = view.findViewById(R.id.fileRecycler)
        summaryScroll = view.findViewById(R.id.summaryScroll)
        summaryText = view.findViewById(R.id.summaryText)
        fileTabContainer = view.findViewById(R.id.fileTabContainer)
        fileEmptyState = view.findViewById(R.id.fileEmptyState)
        emptyDecisionsHint = view.findViewById(R.id.emptyDecisionsHint)

        val decisionRecycler = view.findViewById<RecyclerView>(R.id.decisionRecycler)
        val actionRecycler = view.findViewById<RecyclerView>(R.id.actionRecycler)

        titleView.text = meetingTitle
        fileRecycler.layoutManager = LinearLayoutManager(requireContext())
        val wrapLmDecisions = WrappingLinearLayoutManager(requireContext())
        val wrapLmActions = WrappingLinearLayoutManager(requireContext())
        decisionRecycler.layoutManager = wrapLmDecisions
        decisionRecycler.isNestedScrollingEnabled = false
        actionRecycler.layoutManager = wrapLmActions
        actionRecycler.isNestedScrollingEnabled = false

        decisionAdapter =
            DecisionAdapter(
                items = mutableListOf(),
                onEdit = { item, position -> showEditDecisionDialog(item, position) },
                onDelete = { item, position -> confirmDeleteDecision(item, position) },
            )
        decisionRecycler.adapter = decisionAdapter

        actionAdapter =
            ActionAdapter(
                items = mutableListOf(),
                onEdit = { item, position -> showEditActionDialog(item, position) },
                onDelete = { item, position -> confirmDeleteAction(item, position) },
            )
        actionRecycler.adapter = actionAdapter

        summaryText.text = "—"
        meetingAttendeesView.text = getString(R.string.detail_attendees_empty)
        meetingAttendeesView.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.color_text_secondary),
        )

        view.findViewById<View>(R.id.btnEditMeetingInfo).setOnClickListener {
            showEditMeetingInfoDialog()
        }
        view.findViewById<View>(R.id.btnAddDecision).setOnClickListener { showAddDecisionDialog() }
        view.findViewById<View>(R.id.btnAddAction).setOnClickListener { showAddActionDialog() }

        setupListeners(view)
        setupToolbarMenu()
        updateTabs(summaryBtn, fileBtn)
        showSummary(summaryScroll, fileTabContainer)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> applyUiState(state) }
                }
                launch {
                    viewModel.effects.collect { effect -> handleEffect(effect, view) }
                }
            }
        }

        if (meetingId > 0) {
            viewModel.loadInitial()
        } else {
            meetingDateView.text = "—"
            meetingTimeView.text = "—"
            bindAttendees(null)
        }
    }

    private fun setupToolbarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_detail, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_delete_meeting -> {
                            showDeleteMeetingDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun applyUiState(state: MeetingDetailUiState) {
        detailProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        val meeting = state.meeting
        if (meeting != null) {
            titleView.text = meeting.title.ifBlank { meetingTitle }
            meetingDateView.text = meeting.meetingDate?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
            meetingTimeView.text = meeting.meetingTime?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
            bindAttendees(meeting)
            meeting.serverFilePaths?.let { paths ->
                sessionViewModel.rememberServerFilePaths(meeting.id, paths)
            }
            if (!isSummaryTab) {
                bindMeetingFileList()
            }
        } else if (meetingId <= 0) {
            titleView.text = meetingTitle
            meetingDateView.text = "—"
            meetingTimeView.text = "—"
            bindAttendees(null)
        }
        state.summary?.let { bindSummaryDetail(it) }
    }

    private fun bindAttendees(meeting: MeetingResponseDto?) {
        val names =
            meeting?.attendees
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (names.isEmpty()) {
            meetingAttendeesView.text = getString(R.string.detail_attendees_empty)
            meetingAttendeesView.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.color_text_secondary),
            )
        } else {
            meetingAttendeesView.text = names.joinToString(", ")
            meetingAttendeesView.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.color_text_primary),
            )
        }
    }

    private fun handleEffect(effect: MeetingDetailEffect, root: View) {
        when (effect) {
            is MeetingDetailEffect.Toast ->
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()

            is MeetingDetailEffect.Error ->
                Snackbar.make(root, effect.message, Snackbar.LENGTH_LONG).show()

            MeetingDetailEffect.NavigateToLogin -> {
                TokenManager.clear()
                findNavController().navigate(R.id.loginFragment)
            }

            MeetingDetailEffect.MeetingDeleted -> {
                Toast.makeText(requireContext(), "회의가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun showEditMeetingInfoDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_meeting_info, null, false)
        val inputTitle = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingTitle)
        val inputDate = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingDate)
        val inputTime = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingTime)
        val inputAttendees = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingAttendees)

        val m = viewModel.uiState.value.meeting
        inputTitle.setText(m?.title ?: titleView.text?.toString().orEmpty())
        inputDate.setText(
            when {
                m?.meetingDate?.isNotBlank() == true -> m.meetingDate
                meetingDateView.text.toString() != "—" -> meetingDateView.text.toString()
                else -> ""
            },
        )
        inputTime.setText(
            when {
                m?.meetingTime?.isNotBlank() == true -> m.meetingTime
                meetingTimeView.text.toString() != "—" -> meetingTimeView.text.toString()
                else -> ""
            },
        )
        inputAttendees.setText(m?.attendees?.joinToString(", ").orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle("회의 정보 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val title = inputTitle.text?.toString().orEmpty()
                if (title.isBlank()) {
                    Toast.makeText(requireContext(), "제목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val date = inputDate.text?.toString().orEmpty()
                val time = inputTime.text?.toString().orEmpty()
                val attendees = inputAttendees.text?.toString().orEmpty()
                viewModel.updateMeetingInfo(title, date, time, attendees)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setupListeners(view: View) {
        summaryBtn.setOnClickListener {
            isSummaryTab = true
            updateTabs(summaryBtn, fileBtn)
            showSummary(summaryScroll, fileTabContainer)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            updateTabs(summaryBtn, fileBtn)
            showFiles(summaryScroll, fileTabContainer)
        }

        view.findViewById<View>(R.id.btnEditSummary).setOnClickListener {
            showEditSummaryDialog()
        }
    }

    private fun showDeleteMeetingDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "삭제할 회의 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("회의 삭제")
            .setMessage("삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteMeeting() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun bindSummaryDetail(detail: SummaryDetailResponseDto) {
        summaryText.text = detail.summary.trim().ifBlank { "—" }
        val decisionItems =
            detail.decisions?.map { it.toDecisionItem() }.orEmpty()
        decisionAdapter.replaceAll(decisionItems)
        emptyDecisionsHint.visibility =
            if (decisionItems.isEmpty()) View.VISIBLE else View.GONE
        actionAdapter.replaceAll(
            detail.actionItems?.map { it.toDetailTaskItem() }.orEmpty(),
        )
    }

    private fun showEditSummaryDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_summary, null, false)
        val etSummary = dialogView.findViewById<TextInputEditText>(R.id.etSummary)
        etSummary.setText(
            if (summaryText.text.toString() == "—") "" else summaryText.text.toString(),
        )

        AlertDialog.Builder(requireContext())
            .setTitle("회의 요약 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val text = etSummary.text?.toString().orEmpty().trim()
                viewModel.patchSummaryBody(text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddDecisionDialog() {
        if (!ensureHasMeetingId()) return
        val editText = EditText(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 추가")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                viewModel.addDecision(text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditDecisionDialog(item: DecisionItem, position: Int) {
        if (!ensureHasMeetingId()) return
        val editText = EditText(requireContext())
        editText.setText(item.content)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 수정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                viewModel.updateDecision(item.id, text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteDecision(item: DecisionItem, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 결정 사항을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                if (!ensureHasMeetingId()) return@setPositiveButton
                viewModel.deleteDecision(item.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddActionDialog() {
        if (!ensureHasMeetingId()) return
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_item, null, false)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTaskTitle)
        val ownerInput = dialogView.findViewById<EditText>(R.id.editTaskOwner)
        val deadlineInput = dialogView.findViewById<EditText>(R.id.editTaskDeadline)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("할 일 추가")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val titleText = titleInput.text.toString().trim()
                if (titleText.isEmpty()) return@setPositiveButton
                val owner = ownerInput.text.toString().trim()
                val deadline = deadlineInput.text.toString().trim()
                viewModel.addActionItem(
                    task = titleText,
                    assignee = owner.ifBlank { null },
                    dueDate = deadline.ifBlank { null },
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditActionDialog(item: DetailTaskItem, position: Int) {
        if (!ensureHasMeetingId()) return
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_item, null, false)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTaskTitle)
        val ownerInput = dialogView.findViewById<EditText>(R.id.editTaskOwner)
        val deadlineInput = dialogView.findViewById<EditText>(R.id.editTaskDeadline)
        titleInput.setText(item.title)
        ownerInput.setText(item.owner)
        deadlineInput.setText(item.deadline)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("할 일 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val titleText = titleInput.text.toString().trim()
                if (titleText.isEmpty()) return@setPositiveButton
                val owner = ownerInput.text.toString().trim()
                val deadline = deadlineInput.text.toString().trim()
                viewModel.updateActionItem(
                    actionItemId = item.id,
                    task = titleText,
                    assignee = owner.ifBlank { null },
                    dueDate = deadline.ifBlank { null },
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteAction(item: DetailTaskItem, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 할 일을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                if (!ensureHasMeetingId()) return@setPositiveButton
                viewModel.deleteActionItem(item.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun ensureHasMeetingId(): Boolean {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateTabs(summaryBtn: MaterialButton, fileBtn: MaterialButton) {
        val primary = ContextCompat.getColor(requireContext(), R.color.color_primary)
        val onPrimary = ContextCompat.getColor(requireContext(), R.color.color_on_primary)
        val surface = ContextCompat.getColor(requireContext(), R.color.color_surface)
        val textPrimary = ContextCompat.getColor(requireContext(), R.color.color_text_primary)
        val line = ContextCompat.getColor(requireContext(), R.color.moa_line)
        val strokePx = (resources.displayMetrics.density * 1f).toInt().coerceAtLeast(1)
        if (isSummaryTab) {
            applyTabSelected(summaryBtn, primary, onPrimary)
            applyTabUnselected(fileBtn, surface, textPrimary, line, strokePx)
        } else {
            applyTabUnselected(summaryBtn, surface, textPrimary, line, strokePx)
            applyTabSelected(fileBtn, primary, onPrimary)
        }
    }

    private fun applyTabSelected(btn: MaterialButton, bg: Int, fg: Int) {
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        btn.strokeWidth = 0
        btn.setTextColor(fg)
    }

    private fun applyTabUnselected(
        btn: MaterialButton,
        bg: Int,
        fg: Int,
        stroke: Int,
        strokePx: Int,
    ) {
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        btn.strokeColor = android.content.res.ColorStateList.valueOf(stroke)
        btn.strokeWidth = strokePx
        btn.setTextColor(fg)
    }

    private fun showSummary(summaryScroll: View, fileTabArea: View) {
        summaryScroll.visibility = View.VISIBLE
        fileTabArea.visibility = View.GONE
    }

    private fun showFiles(summaryScroll: View, fileTabArea: View) {
        summaryScroll.visibility = View.GONE
        fileTabArea.visibility = View.VISIBLE
        if (meetingId > 0) {
            viewModel.refreshMeetingHeader()
        }
        bindMeetingFileList()
    }

    /** API로 받은 경로 + 생성 플로우 세션 캐시(동일 회의) 병합 */
    private fun resolvedServerFilePaths(): List<String> {
        val fromApi =
            viewModel.uiState.value.meeting
                ?.serverFilePaths
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val fromSession =
            sessionViewModel.getServerFilePaths(meetingId).map { it.trim() }.filter { it.isNotEmpty() }
        val ordered = LinkedHashSet<String>()
        fromApi.forEach { ordered.add(it) }
        fromSession.forEach { ordered.add(it) }
        return ordered.toList()
    }

    /**
     * 서버 경로(API·세션) + 이 회의 전용 로컬 목록(`meeting_{id}_files_json` 또는 제목 키).
     */
    private fun bindMeetingFileList() {
        val rows = mergedMeetingFileRows()
        if (rows.isEmpty()) {
            fileRecycler.adapter = null
            fileRecycler.visibility = View.GONE
            fileEmptyState.visibility = View.VISIBLE
            return
        }
        fileEmptyState.visibility = View.GONE
        fileRecycler.visibility = View.VISIBLE
        fileRecycler.adapter =
            MeetingFileAdapter(rows) { row -> onMeetingFileRowClicked(row) }
    }

    private fun mergedMeetingFileRows(): List<MeetingFileRow> {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<MeetingFileRow>()
        fun add(row: MeetingFileRow) {
            val key =
                when {
                    !row.downloadUrl.isNullOrBlank() -> "d:${row.downloadUrl}"
                    row.localPath.isNotBlank() -> "l:${File(row.localPath).absolutePath}"
                    else -> "x:${row.title}:${row.subtitle}"
                }
            if (seen.add(key)) out.add(row)
        }
        resolvedServerFilePaths().map { pathToMeetingFileRow(it) }.forEach(::add)
        loadMeetingFilesFromLocalPrefs().forEach(::add)
        return out
    }

    /**
     * [meetingId]가 있으면 `meeting_{id}_files_json`만 사용(다른 회의와 분리).
     * 예전 데이터는 제목 키에서 한 번만 ID 키로 복사한다.
     * [meetingId]가 없으면 초안용 제목 키만 사용한다.
     */
    private fun loadMeetingFilesFromLocalPrefs(): List<MeetingFileRow> {
        val prefs = MeetingLocalFilesPrefs.prefs(requireContext())
        if (meetingId > 0) {
            MeetingLocalFilesPrefs.copyTitleKeyToMeetingIdIfEmpty(
                requireContext(),
                gson,
                meetingTitle,
                meetingId,
            )
            val idKey = MeetingLocalFilesPrefs.meetingIdKey(meetingId) ?: return emptyList()
            return MeetingLocalFilesPrefs.readList(prefs, gson, idKey)
        }
        return MeetingLocalFilesPrefs.readList(
            prefs,
            gson,
            MeetingLocalFilesPrefs.titleKey(meetingTitle),
        )
    }

    private fun onMeetingFileRowClicked(row: MeetingFileRow) {
        when {
            row.localPath.isNotBlank() -> {
                val file = File(row.localPath)
                if (file.exists()) {
                    openLocalMeetingFile(file)
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.detail_file_not_found),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            !row.downloadUrl.isNullOrBlank() -> downloadFromServer(row)
            else ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.detail_download_failed),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    private fun openLocalMeetingFile(file: File) {
        val uri =
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
        val mimeType = getMimeTypeForFile(file)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                getString(R.string.detail_no_viewer_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun getMimeTypeForFile(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun pathToMeetingFileRow(serverPath: String): MeetingFileRow {
        val trimmed = serverPath.trim()
        val name = trimmed.substringAfterLast('/').ifBlank { trimmed }
        val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val type =
            when (ext) {
                "pdf" -> MeetingFileRow.Type.PDF
                "jpg", "jpeg", "png", "webp", "gif" -> MeetingFileRow.Type.IMAGE
                "m4a", "mp3", "wav", "aac", "ogg", "flac" -> MeetingFileRow.Type.AUDIO
                else -> MeetingFileRow.Type.DOCUMENT
            }
        val label = ext.uppercase(Locale.getDefault()).ifBlank { "FILE" }
        return MeetingFileRow(
            title = name,
            subtitle = label,
            localPath = "",
            displayName = name,
            type = type,
            downloadUrl = resolveServerDownloadUrl(trimmed),
        )
    }

    private fun resolveServerDownloadUrl(pathOrUrl: String): String {
        val p = pathOrUrl.trim()
        if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) {
            return p
        }
        val base = BuildConfig.MOA_API_BASE_URL.trimEnd('/')
        return "$base/${p.removePrefix("/")}"
    }

    private fun downloadFromServer(row: MeetingFileRow) {
        val url = row.downloadUrl ?: return
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request =
            DownloadManager.Request(Uri.parse(url))
                .setTitle(row.displayName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
        TokenManager.getAccessToken()?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
            request.addRequestHeader("Authorization", "Bearer $token")
        }
        runCatching {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MOA/${row.displayName}")
            dm.enqueue(request)
        }.onSuccess {
            Toast.makeText(requireContext(), getString(R.string.detail_download_started), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), getString(R.string.detail_download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun DecisionDto.toDecisionItem(): DecisionItem =
        DecisionItem(
            id = id,
            meetingId = meetingId,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ActionItemDto.toDetailTaskItem(): DetailTaskItem =
        DetailTaskItem(
            id = id,
            meetingId = meetingId,
            title = task,
            owner = assignee.orEmpty(),
            deadline = dueDate.orEmpty(),
        )
}

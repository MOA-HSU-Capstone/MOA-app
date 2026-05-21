package com.example.a20260310.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.example.a20260310.viewmodel.SummaryProgressState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.repository.MeetingRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels {
        MeetingSessionViewModel.factory(requireActivity().application)
    }
    private val meetingRepository = MeetingRepository()
    private lateinit var recycler: RecyclerView
    private lateinit var folderTabs: LinearLayout
    private var selectedFolder: String = "전체"
    private var completionToastShown = false
    private var previousSummaryExpanded: Boolean? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recycler)
        folderTabs = view.findViewById(R.id.folderTabs)

        recycler.layoutManager = LinearLayoutManager(context)

        setupFolderTabs()
        loadList()

        view.findViewById<MaterialButton>(R.id.addMeetingButton).setOnClickListener {
            sessionViewModel.clearNewMeetingAttachmentSelection()
            findNavController().navigate(R.id.action_homeFragment_to_folderSelectFragment)
        }

        val homeRoot = view.findViewById<ConstraintLayout>(R.id.homeRoot)
        val summaryPanelHost = view.findViewById<MaterialCardView>(R.id.summaryPanelHost)
        val summaryPanelCollapsedInner = view.findViewById<View>(R.id.summaryPanelCollapsedInner)
        val summaryPanelExpandedInner = view.findViewById<View>(R.id.summaryPanelExpandedInner)
        val panelCollapsedCircular =
            view.findViewById<CircularProgressIndicator>(R.id.panelCollapsedCircularProgress)
        val panelCollapsedCompleteGroup = view.findViewById<View>(R.id.panelCollapsedCompleteGroup)
        val panelCollapsedCheck = view.findViewById<ImageView>(R.id.panelCollapsedCheck)
        val panelCollapsedCompleteLabel =
            view.findViewById<TextView>(R.id.panelCollapsedCompleteLabel)
        val groupSummaryExpandedProgress = view.findViewById<Group>(R.id.groupSummaryExpandedProgress)
        val panelExpandedQueueBanner = view.findViewById<TextView>(R.id.panelExpandedQueueBanner)
        val panelCollapsedSubtitle = view.findViewById<TextView>(R.id.panelCollapsedSubtitle)
        val panelExpandedCompleteInner = view.findViewById<View>(R.id.panelExpandedCompleteInner)
        val panelExpandedTitleComplete =
            view.findViewById<TextView>(R.id.panelExpandedTitleComplete)
        val panelExpandedConfirmButton =
            view.findViewById<MaterialButton>(R.id.panelExpandedConfirmButton)
        val panelExpandedTitle = view.findViewById<TextView>(R.id.panelExpandedTitle)
        val panelExpandedEta = view.findViewById<TextView>(R.id.panelExpandedEta)
        val panelExpandedPercent = view.findViewById<TextView>(R.id.panelExpandedPercent)
        val panelExpandedCollapseChevron =
            view.findViewById<MaterialButton>(R.id.panelExpandedCollapseChevron)
        val panelProgressBar = view.findViewById<LinearProgressIndicator>(R.id.panelProgressBar)
        val panelSidePx =
            resources.getDimensionPixelSize(R.dimen.summary_panel_min_height)

        fun refreshSummaryPanel(animateLayout: Boolean) {
            val state = sessionViewModel.summaryProgress.value ?: SummaryProgressState.idle()
            val expanded = sessionViewModel.summaryPanelExpanded.value == true
            val runningNow = state.isRunning && !state.isComplete
            val waitingOnly = !state.isRunning && !state.isComplete && state.waitingCount > 0
            val complete = state.isComplete
            val show = runningNow || complete || waitingOnly

            if (animateLayout && show) {
                TransitionManager.beginDelayedTransition(homeRoot, ChangeBounds())
            }

            summaryPanelHost.isVisible = show
            if (!show) return

            val lp = summaryPanelHost.layoutParams as ConstraintLayout.LayoutParams
            if (expanded) {
                lp.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                lp.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                lp.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
                lp.horizontalBias = 0.5f
            } else {
                lp.width = panelSidePx
                lp.height = panelSidePx
                lp.horizontalBias = 1f
            }
            summaryPanelHost.layoutParams = lp

            summaryPanelCollapsedInner.isVisible = !expanded
            summaryPanelExpandedInner.isVisible = expanded

            val workingHeader = expanded && !complete && (runningNow || waitingOnly)
            panelExpandedTitle.isVisible = workingHeader
            panelExpandedCollapseChevron.isVisible = workingHeader
            panelExpandedEta.isVisible = expanded && runningNow
            if (expanded && runningNow) {
                panelExpandedEta.text = formatEtaShort(state.etaSecondsRemaining)
            }

            groupSummaryExpandedProgress.isVisible = expanded && runningNow
            panelExpandedCompleteInner.isVisible = expanded && complete

            val showQueueBanner = expanded && !complete && state.waitingCount > 0
            panelExpandedQueueBanner.isVisible = showQueueBanner
            if (showQueueBanner) {
                panelExpandedQueueBanner.text =
                    getString(R.string.summary_panel_queue_banner, state.waitingCount)
            }

            panelExpandedTitle.text = state.meetingTitle
            panelExpandedTitleComplete.text = state.meetingTitle
            panelProgressBar.setProgressCompat(state.progressPercent.coerceIn(0, 100), true)
            panelExpandedPercent.text = "${state.progressPercent}%"

            panelCollapsedCircular.isVisible = !expanded && runningNow
            if (!expanded && runningNow) {
                panelCollapsedCircular.setProgressCompat(state.progressPercent.coerceIn(0, 100), true)
            }

            val showCollapsedQueueHint =
                !expanded && !complete && state.waitingCount > 0 &&
                    (runningNow || waitingOnly)
            panelCollapsedSubtitle.isVisible = showCollapsedQueueHint
            if (showCollapsedQueueHint) {
                panelCollapsedSubtitle.text =
                    getString(R.string.summary_panel_queue_banner, state.waitingCount)
            }

            panelCollapsedCompleteGroup.isVisible = !expanded && complete
            if (!expanded && complete) {
                panelCollapsedCheck.setImageResource(
                    if (state.summarySucceeded) R.drawable.ic_done else R.drawable.ic_error,
                )
                panelCollapsedCheck.contentDescription = getString(
                    if (state.summarySucceeded) R.string.summary_panel_complete
                    else R.string.summary_panel_failed_short,
                )
                panelCollapsedCompleteLabel.text = getString(
                    if (state.summarySucceeded) R.string.summary_panel_complete
                    else R.string.summary_panel_failed_short,
                )
            }

            summaryPanelHost.isClickable = true
            summaryPanelHost.isFocusable = true
        }

        view.findViewById<MaterialButton>(R.id.panelExpandedCollapseChevron).setOnClickListener {
            sessionViewModel.setSummaryPanelExpanded(false)
        }

        panelExpandedConfirmButton.setOnClickListener {
            val state = sessionViewModel.summaryProgress.value ?: return@setOnClickListener
            val meetingTitle = state.meetingTitle.trim().ifBlank { "회의" }

            sessionViewModel.dismissSummaryProgressPanel()

            val bundle = Bundle().apply {
                sessionViewModel.currentBackendMeetingId.value?.let {
                    putInt("meetingId", it)
                }
                putString("meetingTitle", meetingTitle)
            }

            findNavController().navigate(
                R.id.action_homeFragment_to_detailFragment,
                bundle
            )
        }

        summaryPanelHost.setOnClickListener {
            val state = sessionViewModel.summaryProgress.value ?: return@setOnClickListener
            val expanded = sessionViewModel.summaryPanelExpanded.value == true
            val hasMinutes = sessionViewModel.minutes.value != null
            val waitingOnly = !state.isRunning && !state.isComplete && state.waitingCount > 0

            when {
                state.isComplete && hasMinutes && !expanded -> {
                    sessionViewModel.dismissSummaryProgressPanel()

                    val bundle = Bundle().apply {
                        sessionViewModel.currentBackendMeetingId.value?.let {
                            putInt("meetingId", it)
                        }
                        putString("meetingTitle", state.meetingTitle)
                    }

                    findNavController().navigate(
                        R.id.action_homeFragment_to_detailFragment,
                        bundle
                    )
                }

                state.isComplete && hasMinutes && expanded -> Unit

                waitingOnly && !expanded -> {
                    sessionViewModel.setSummaryPanelExpanded(true)
                }

                !expanded -> {
                    sessionViewModel.setSummaryPanelExpanded(true)
                }
            }
        }

        sessionViewModel.summaryProgress.observe(viewLifecycleOwner) { state ->
            refreshSummaryPanel(animateLayout = false)

            if (state.isRunning) {
                completionToastShown = false
            }
            if (state.isComplete &&
                isResumed &&
                !completionToastShown
            ) {
                completionToastShown = true
                Toast.makeText(
                    requireContext(),
                    R.string.summary_complete_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        sessionViewModel.summaryPanelExpanded.observe(viewLifecycleOwner) { expanded ->
            val animate =
                previousSummaryExpanded != null && previousSummaryExpanded != expanded
            previousSummaryExpanded = expanded
            refreshSummaryPanel(animateLayout = animate)
        }
    }

    private fun formatEtaShort(seconds: Long?): String {
        val total = seconds ?: return ""
        val s = total.coerceAtLeast(0L)
        val min = (s / 60).toInt()
        return if (min >= 1) {
            getString(R.string.summary_panel_eta_minutes_left, min)
        } else {
            getString(R.string.summary_panel_eta_seconds_left, s.toInt().coerceAtLeast(1))
        }
    }

    private fun setupFolderTabs() {
        folderTabs.removeAllViews()

        val folders = mutableListOf("전체")
        folders.addAll(getFolderNames(requireContext()))

        folders.forEach { name ->
            val btn = createFolderButton(name)
            folderTabs.addView(btn)
        }

        updateTabs()
    }

    private fun createFolderButton(name: String): MaterialButton {

        val btn = MaterialButton(requireContext())

        btn.text = name

        // 직사각형
        btn.cornerRadius = 0

        // 크기
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.marginEnd = 0
        btn.layoutParams = params

        // 내부 여백
        btn.setPadding(48, 20, 48, 20)

        // 테두리
        btn.strokeWidth = 2
        btn.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.color_divider)
        )

        // 기본 색상
        btn.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.color_background)
        )

        btn.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.color_text_secondary)
        )

        btn.setOnClickListener {
            selectedFolder = name
            updateTabs()
            loadList()
        }

        return btn
    }

    private fun updateTabs() {

        for (i in 0 until folderTabs.childCount) {

            val btn = folderTabs.getChildAt(i) as MaterialButton

            if (btn.text == selectedFolder) {

                btn.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.color_primary)
                )

                btn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.color_on_primary)
                )

            } else {

                btn.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.color_background)
                )

                btn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.color_text_secondary)
                )
            }
        }
    }

    private fun loadList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("moa_prefs", Context.MODE_PRIVATE)

            runCatching {
                meetingRepository.getMeetings()
            }.onSuccess { meetings ->

                val filteredMeetings = if (selectedFolder == "전체") {
                    meetings
                } else {
                    meetings.filter { meeting ->
                        val savedFolder = prefs.getString("meeting_${meeting.id}_folder", null)
                        android.util.Log.d("HomeFragment", "meetingId=${meeting.id}, savedFolder=$savedFolder, selected=$selectedFolder")
                        savedFolder == selectedFolder
                    }
                }

                val items = filteredMeetings.map { meeting ->
                    SimpleRow(
                        title = meeting.title,
                        subtitle = meeting.displayMeetingSchedule().ifBlank { "일정 없음" },
                        meetingId = meeting.id,
                    )
                }

                recycler.adapter = SimpleRowAdapter(items) { item ->
                    val bundle = Bundle().apply {
                        item.meetingId?.let { putInt("meetingId", it) }
                        putString("meetingTitle", item.title)
                    }

                    findNavController().navigate(
                        R.id.action_homeFragment_to_detailFragment,
                        bundle
                    )
                }
            }.onFailure { error ->
                if (error is HttpException && error.code() == 401) {
                    TokenManager.clear()
                    findNavController().navigate(R.id.loginFragment)
                    return@onFailure
                }

                Toast.makeText(
                    requireContext(),
                    error.message ?: "회의 목록을 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()

                recycler.adapter = SimpleRowAdapter(emptyList()) {}
            }
        }
    }

    private fun com.example.a20260310.data.remote.dto.MeetingResponseDto.displayMeetingSchedule(): String {
        return listOfNotNull(
            meetingDate?.trim()?.takeIf { it.isNotEmpty() },
            meetingTime?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
    }
}

fun getFolderNames(context: Context): List<String> {
    val prefs = context.getSharedPreferences("moa_prefs", 0)
    return prefs.getStringSet("folder_list", setOf())?.toList() ?: emptyList()
}

fun getFilesByFolder(context: Context, folderName: String): List<SimpleRow> {

    val prefs = context.getSharedPreferences("moa_prefs", 0)

    val folder = File(context.filesDir, "MOA/$folderName")

    if (!folder.exists()) return emptyList()

    return folder.listFiles()
        ?.filter { it.name.endsWith(".m4a") }
        ?.sortedByDescending { it.lastModified() }
        ?.map {
            val title = prefs.getString(it.name, it.name)
            val date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                .format(Date(it.lastModified()))

            SimpleRow(title ?: it.name, date)
        } ?: emptyList()
}

fun getAllFiles(context: Context): List<SimpleRow> {

    val baseDir = File(context.filesDir, "MOA")
    val prefs = context.getSharedPreferences("moa_prefs", 0)

    if (!baseDir.exists()) return emptyList()

    return baseDir.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".m4a") }
        .sortedByDescending { it.lastModified() }
        .map {
            val title = prefs.getString(it.name, it.name)
            val date = SimpleDateFormat("yyyy년 M월 d일 a HH:mm", Locale.KOREA)
                .format(Date(it.lastModified()))

            SimpleRow(title ?: it.name, date)
        }.toList()
}
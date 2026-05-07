package com.example.a20260310.ui.detail

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val summaryBtn = view.findViewById<MaterialButton>(R.id.tabSummary)
        val fileBtn = view.findViewById<MaterialButton>(R.id.tabFiles)
        val recycler = view.findViewById<RecyclerView>(R.id.fileRecycler)
        val summaryScroll = view.findViewById<ScrollView>(R.id.summaryScroll)

        val meetingTitle = arguments?.getString("meetingTitle") ?: "회의"
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)

        title.text = meetingTitle
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // ================= 카드 연결 =================

        setupCard(
            parentView = view,
            cardId = R.id.cardSummary,
            titleText = "회의 요약",
            prefKey = "${meetingTitle}_summary",
            defaultText = "요약 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardDecisions,
            titleText = "결정 사항",
            prefKey = "${meetingTitle}_decisions",
            defaultText = "결정 사항 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardActions,
            titleText = "할 일",
            prefKey = "${meetingTitle}_actions",
            defaultText = "할 일 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardParticipants,
            titleText = "담당자",
            prefKey = "${meetingTitle}_participants",
            defaultText = "",
            scrollView = summaryScroll
        )

        // ================= 탭 =================

        updateTabs(summaryBtn, fileBtn)

        summaryBtn.setOnClickListener {
            isSummaryTab = true
            updateTabs(summaryBtn, fileBtn)
            showSummary(summaryScroll, recycler)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            updateTabs(summaryBtn, fileBtn)
            showFiles(summaryScroll, recycler)
        }
    }

    // ================= 카드 공통 처리 =================

    private fun setupCard(
        parentView: View,
        cardId: Int,
        titleText: String,
        prefKey: String,
        defaultText: String,
        scrollView: ScrollView
    ) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)

        val card = parentView.findViewById<View>(cardId)

        val title = card.findViewById<TextView>(R.id.cardTitle)
        val editBtn = card.findViewById<View>(R.id.btnEdit)
        val saveBtn = card.findViewById<View>(R.id.btnSave)
        val editText = card.findViewById<TextInputEditText>(R.id.cardContent)

        // 🔥 제목 설정
        title.text = titleText

        // 🔥 내용 세팅
        editText.setText(prefs.getString(prefKey, defaultText))

        editBtn.setOnClickListener {
            editBtn.visibility = View.GONE
            saveBtn.visibility = View.VISIBLE

            editText.isFocusableInTouchMode = true
            editText.isCursorVisible = true
            editText.requestFocus()

            val imm = requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)

            editText.post {
                scrollView.smoothScrollTo(0, editText.bottom)
            }
        }

        saveBtn.setOnClickListener {
            val text = editText.text.toString()

            prefs.edit().putString(prefKey, text).apply()

            editText.isFocusable = false
            editText.isFocusableInTouchMode = false
            editText.isCursorVisible = false
            editText.clearFocus()

            editBtn.visibility = View.VISIBLE
            saveBtn.visibility = View.GONE

            val imm = requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.hideSoftInputFromWindow(editText.windowToken, 0)

            Toast.makeText(requireContext(), "저장됨", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= 탭 UI =================

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
            Toast.makeText(requireContext(), "${it.title} 다운로드", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= 파일 =================

    private fun loadFiles(): List<SimpleRow> {

        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val baseDir = requireContext().filesDir
        val meetingTitle = arguments?.getString("meetingTitle")

        return baseDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".m4a") }
            .filter { prefs.getString(it.name, "") == meetingTitle }
            .map { SimpleRow(it.name, "${it.length() / 1024} KB") }
            .toList()
    }
}
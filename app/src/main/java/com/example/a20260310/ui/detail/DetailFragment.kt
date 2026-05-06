package com.example.a20260310.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.home.HomeFragment
import com.google.android.material.button.MaterialButton

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val summaryBtn = view.findViewById<MaterialButton>(R.id.tabSummary)
        val fileBtn = view.findViewById<MaterialButton>(R.id.tabFiles)
        val content = view.findViewById<TextView>(R.id.content)
        val recycler = view.findViewById<RecyclerView>(R.id.fileRecycler)

        val meetingTitle = arguments?.getString("meetingTitle") ?: "회의"
        title.text = meetingTitle

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // 기본 = 요약
        showSummary(content, recycler)

        summaryBtn.setOnClickListener {
            isSummaryTab = true
            showSummary(content, recycler)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            showFiles(recycler, content)
        }
    }

    private fun showSummary(content: TextView, recycler: RecyclerView) {
        recycler.visibility = View.GONE
        content.visibility = View.VISIBLE

        //나중에 수정
        //content.text = "1. 해시 함수 이해\n- 요약 내용 예시"
    }

    private fun showFiles(recycler: RecyclerView, content: TextView) {
        content.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        val files = loadFiles()

        recycler.adapter = HomeFragment.SimpleRowAdapter(files) {}
    }

    private fun loadFiles(): List<SimpleRow> {
        val dir = requireContext().filesDir
        return dir.listFiles()
            ?.filter { it.name.endsWith(".m4a") }
            ?.map {
                SimpleRow(it.name, "${it.length() / 1024} KB")
            } ?: emptyList()
    }
}
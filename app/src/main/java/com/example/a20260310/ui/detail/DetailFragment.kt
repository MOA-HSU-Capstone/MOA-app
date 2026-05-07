package com.example.a20260310.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.google.android.material.button.MaterialButton

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val summaryBtn = view.findViewById<MaterialButton>(R.id.tabSummary)
        val fileBtn = view.findViewById<MaterialButton>(R.id.tabFiles)
        //val content = view.findViewById<TextView>(R.id.content)
        val recycler = view.findViewById<RecyclerView>(R.id.fileRecycler)
        val summaryScroll = view.findViewById<View>(R.id.summaryScroll)
        val meetingTitle = arguments?.getString("meetingTitle") ?: "회의"
        title.text = meetingTitle

        recycler.layoutManager = LinearLayoutManager(requireContext())

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

        recycler.adapter = SimpleRowAdapter(files) {}
    }

    private fun loadFiles(): List<SimpleRow> {

        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val baseDir = requireContext().filesDir

        val meetingTitle = arguments?.getString("meetingTitle")

        return baseDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".m4a") }
            .filter { file ->
                val savedTitle = prefs.getString(file.name, "")
                savedTitle == meetingTitle   // 🔥 핵심 필터
            }
            .map {
                SimpleRow(it.name, "${it.length() / 1024} KB")
            }
            .toList()
    }
}
package com.example.a20260310.ui.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter

class HistoryFragment : Fragment(R.layout.fragment_history) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val items = listOf(
            SimpleRow(title = "회의 #12 - 사용자 테스트", subtitle = "2026-03-31 • 55분 • 액션 8개"),
            SimpleRow(title = "회의 #11 - API 설계", subtitle = "2026-03-27 • 44분 • 요약 생성됨"),
            SimpleRow(title = "회의 #10 - UI 프로토타입", subtitle = "2026-03-20 • 25분 • 요약 생성됨"),
            SimpleRow(title = "회의 #09 - 요구사항 정리", subtitle = "2026-03-18 • 38분 • 액션 6개"),
        )

        view.findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SimpleRowAdapter(
                items,
                onClick = TODO()
            )
        }
    }
}

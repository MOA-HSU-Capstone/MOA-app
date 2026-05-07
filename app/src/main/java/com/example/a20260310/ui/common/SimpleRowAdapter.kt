package com.example.a20260310.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow

class SimpleRowAdapter(
    private val items: List<SimpleRow>,
    private val onClick: (SimpleRow) -> Unit
) : RecyclerView.Adapter<SimpleRowAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: SimpleRow) {
            view.findViewById<TextView>(R.id.title).text = item.title
            view.findViewById<TextView>(R.id.subtitle).text = item.subtitle

            view.setOnClickListener {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_row, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
}

package com.example.a20260310.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.MeetingFileRow

class MeetingFileAdapter(
    private val items: List<MeetingFileRow>,
    private val onOpenClick: (MeetingFileRow) -> Unit
) : RecyclerView.Adapter<MeetingFileAdapter.FileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position], onOpenClick)
    }

    override fun getItemCount(): Int = items.size

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon = itemView.findViewById<ImageView>(R.id.fileIcon)
        private val fileTitle = itemView.findViewById<TextView>(R.id.fileTitle)
        private val fileMeta = itemView.findViewById<TextView>(R.id.fileMeta)
        private val btnOpenFile = itemView.findViewById<ImageButton>(R.id.btnOpenFile)

        fun bind(item: MeetingFileRow, onOpenClick: (MeetingFileRow) -> Unit) {
            fileTitle.text = item.title
            fileMeta.text = item.subtitle

            val iconRes = when (item.type) {
                MeetingFileRow.Type.AUDIO -> R.drawable.ic_recording
                MeetingFileRow.Type.IMAGE -> R.drawable.ic_camera
                MeetingFileRow.Type.PDF -> R.drawable.ic_document
                MeetingFileRow.Type.DOCUMENT -> R.drawable.ic_document
            }
            fileIcon.setImageResource(iconRes)

            itemView.setOnClickListener { onOpenClick(item) }
            btnOpenFile.setOnClickListener { onOpenClick(item) }
        }
    }
}
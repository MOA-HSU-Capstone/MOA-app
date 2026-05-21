package com.example.a20260310.ui.add

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.remote.dto.FolderDto

sealed class FolderUiItem {
    data class Folder(val id: Int, val name: String) : FolderUiItem()
    object Add : FolderUiItem()
}

class FolderAdapter(
    private val onFolderClick: (FolderUiItem.Folder) -> Unit,
    private val onAddClick: () -> Unit,
    private val onFolderLongClick: (FolderUiItem.Folder) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FolderUiItem>()
    private var selectedId: Int? = null

    fun submitList(folders: List<FolderDto>) {
        items.clear()
        items.addAll(folders.map { FolderUiItem.Folder(it.id, it.name) })
        items.add(FolderUiItem.Add)
        notifyDataSetChanged()
    }

    fun setSelected(folderId: Int?) {
        selectedId = folderId
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FolderUiItem.Folder -> 0
            FolderUiItem.Add -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            FolderViewHolder(inflater.inflate(R.layout.item_folder, parent, false))
        } else {
            AddViewHolder(inflater.inflate(R.layout.item_add_folder, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FolderUiItem.Folder -> (holder as FolderViewHolder).bind(item)
            FolderUiItem.Add -> (holder as AddViewHolder).bind()
        }
    }

    override fun getItemCount(): Int = items.size

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(folder: FolderUiItem.Folder) {
            val title = itemView.findViewById<TextView>(R.id.folderName)
            title.text = folder.name

            itemView.setOnClickListener {
                selectedId = folder.id
                notifyDataSetChanged()
                onFolderClick(folder)
            }

            itemView.setOnLongClickListener {
                onFolderLongClick(folder)
                true
            }

            itemView.alpha = if (folder.id == selectedId) 1.0f else 0.3f
            itemView.scaleX = if (folder.id == selectedId) 1.05f else 1.0f
            itemView.scaleY = if (folder.id == selectedId) 1.05f else 1.0f
        }
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            itemView.setOnClickListener { onAddClick() }
        }
    }
}
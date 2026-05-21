package com.example.a20260310.ui.add

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.repository.FolderRepository
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class FolderSelectFragment : Fragment(R.layout.fragment_folder_select) {

    private lateinit var adapter: FolderAdapter
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()
    private val folderRepository = FolderRepository()

    private var selectedFolderId: Int? = null
    private var selectedFolderName: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.folderRecycler)
        recycler.layoutManager = GridLayoutManager(context, 2)

        adapter = FolderAdapter(
            onFolderClick = { folder ->
                selectedFolderId = folder.id
                selectedFolderName = folder.name
            },
            onAddClick = { showAddFolderDialog() },
            onFolderLongClick = { folder ->
                showFolderActionsDialog(folder)
            }
        )

        recycler.adapter = adapter
        recycler.addItemDecoration(GridSpacingItemDecoration(2, 24))

        loadFolders()

        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {
            sessionViewModel.setSelectedFolder(selectedFolderId, selectedFolderName)
            findNavController().navigate(
                R.id.action_folderSelectFragment_to_meetingCreateFragment
            )
        }
    }

    private fun loadFolders() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { folderRepository.getFolders() }
                .onSuccess { folders ->
                    adapter.submitList(folders)
                    adapter.setSelected(selectedFolderId)
                }
                .onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "폴더 목록을 불러오지 못했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showAddFolderDialog() {
        val editText = EditText(requireContext())

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createFolder(name)
                } else {
                    Toast.makeText(requireContext(), "폴더명을 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun createFolder(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { folderRepository.createFolder(name) }
                .onSuccess { created ->
                    selectedFolderId = created.id
                    selectedFolderName = created.name
                    loadFolders()
                    Toast.makeText(requireContext(), "폴더가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "폴더 생성에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showFolderActionsDialog(folder: FolderUiItem.Folder) {
        val items = arrayOf("이름 수정", "삭제")

        AlertDialog.Builder(requireContext())
            .setTitle(folder.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> showDeleteFolderDialog(folder)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRenameFolderDialog(folder: FolderUiItem.Folder) {
        val editText = EditText(requireContext())
        editText.setText(folder.name)
        editText.setSelection(folder.name.length)

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 이름 수정")
            .setView(editText)
            .setPositiveButton("수정") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "폴더명을 입력하세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == folder.name) return@setPositiveButton
                renameFolder(folder.id, newName)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun renameFolder(folderId: Int, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { folderRepository.updateFolder(folderId, newName) }
                .onSuccess { updated ->
                    if (selectedFolderId == updated.id) {
                        selectedFolderName = updated.name
                    }
                    loadFolders()
                    Toast.makeText(requireContext(), "폴더명이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "폴더 이름 수정에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showDeleteFolderDialog(folder: FolderUiItem.Folder) {
        AlertDialog.Builder(requireContext())
            .setTitle("폴더 삭제")
            .setMessage("'${folder.name}' 폴더를 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                deleteFolder(folder)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteFolder(folder: FolderUiItem.Folder) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { folderRepository.deleteFolder(folder.id) }
                .onSuccess {
                    if (selectedFolderId == folder.id) {
                        selectedFolderId = null
                        selectedFolderName = null
                    }
                    loadFolders()
                    Toast.makeText(requireContext(), "폴더가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "폴더 삭제에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount

            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount
            outRect.top = spacing

            if (position < spanCount) {
                outRect.top = spacing
            }
        }
    }
}
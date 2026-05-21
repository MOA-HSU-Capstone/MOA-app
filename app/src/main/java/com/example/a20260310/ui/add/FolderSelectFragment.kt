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
            onFolderLongClick = { _ ->
                Toast.makeText(requireContext(), "폴더 수정/삭제 API 연결 전입니다.", Toast.LENGTH_SHORT).show()
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
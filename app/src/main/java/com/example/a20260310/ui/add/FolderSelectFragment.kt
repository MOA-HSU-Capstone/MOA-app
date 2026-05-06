package com.example.a20260310.ui.add

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.a20260310.R
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.a20260310.ui.recording.getOrCreateFolder
import java.io.File

class FolderSelectFragment : Fragment(R.layout.fragment_folder_select) {

    private lateinit var adapter: FolderAdapter
    private var selectedFolder: String = "전체"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.folderRecycler)

        recycler.layoutManager = GridLayoutManager(context, 2)

        adapter = FolderAdapter(
            onFolderClick = { folder ->
                selectedFolder = folder
            },
            onAddClick = {
                showAddFolderDialog()
            },
            onFolderLongClick = { folder ->
                showFolderOptionsDialog(folder)   // 🔥 변경
            }
        )

        recycler.adapter = adapter

        loadFolders()


        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {

            if (selectedFolder == null) return@setOnClickListener

            val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
            prefs.edit().putString("selected_folder", selectedFolder).apply()

            findNavController().navigate(
                R.id.action_folderSelectFragment_to_meetingCreateFragment
            )
        }
        recycler.addItemDecoration(GridSpacingItemDecoration(2, 24))
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
    private fun loadFolders() {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val folders = prefs.getStringSet("folder_list", setOf()) ?: setOf()

        adapter.submitList(folders.toList())
    }

    private fun showAddFolderDialog() {
        val editText = EditText(requireContext())

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveFolder(name)
                    loadFolders() // 🔥 추가 후 즉시 반영
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveFolder(name: String) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val set = prefs.getStringSet("folder_list", mutableSetOf())!!.toMutableSet()
        set.add(name)
        prefs.edit().putStringSet("folder_list", set).apply()

        getOrCreateFolder(requireContext(), name)
    }

    private fun showFolderOptionsDialog(folderName: String) {

        val options = arrayOf("이름 변경", "삭제")

        AlertDialog.Builder(requireContext())
            .setTitle(folderName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(folderName)
                    1 -> showDeleteDialog(folderName)
                }
            }
            .show()
    }

    private fun showRenameDialog(oldName: String) {

        val editText = EditText(requireContext())
        editText.setText(oldName)

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 이름 변경")
            .setView(editText)
            .setPositiveButton("변경") { _, _ ->

                val newName = editText.text.toString().trim()

                if (newName.isEmpty()) return@setPositiveButton
                if (newName == oldName) return@setPositiveButton

                renameFolder(oldName, newName)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteDialog(folderName: String) {

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 삭제")
            .setMessage("[$folderName] 폴더를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteFolder(folderName)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    private fun renameFolder(oldName: String, newName: String) {

        val baseDir = File(requireContext().filesDir, "MOA")

        val oldFolder = File(baseDir, oldName)
        val newFolder = File(baseDir, newName)

        // 🔥 1. 이미 존재하면 막기
        if (newFolder.exists()) {
            Toast.makeText(requireContext(), "이미 존재하는 폴더입니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 2. 실제 폴더 rename
        val success = oldFolder.renameTo(newFolder)

        if (!success) {
            Toast.makeText(requireContext(), "이름 변경 실패", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 3. prefs 업데이트
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val set = prefs.getStringSet("folder_list", mutableSetOf())!!.toMutableSet()

        set.remove(oldName)
        set.add(newName)

        prefs.edit().putStringSet("folder_list", set).apply()

        // 🔥 4. 선택 폴더 유지
        if (selectedFolder == oldName) {
            selectedFolder = newName
        }

        // 🔥 5. UI 갱신
        loadFolders()
    }
    private fun deleteFolder(folderName: String) {

        // 🔥 1. 실제 폴더 삭제
        val dir = File(requireContext().filesDir, "MOA/$folderName")
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        // 🔥 2. prefs에서 제거
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val set = prefs.getStringSet("folder_list", mutableSetOf())!!.toMutableSet()
        set.remove(folderName)

        prefs.edit().putStringSet("folder_list", set).apply()

        // 🔥 3. 선택 폴더 초기화
        if (selectedFolder == folderName) {
            selectedFolder = set.firstOrNull() ?: ""
        }

        // 🔥 4. UI 갱신
        loadFolders()
    }
    sealed class FolderItem {
        data class Folder(val name: String) : FolderItem()
        object Add : FolderItem()
    }
}
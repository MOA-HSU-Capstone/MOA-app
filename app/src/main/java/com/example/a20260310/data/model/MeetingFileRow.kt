package com.example.a20260310.data.model

data class MeetingFileRow(
    val title: String,
    val subtitle: String,
    val localPath: String,
    val displayName: String,
    val type: Type
) {
    enum class Type {
        AUDIO, IMAGE, PDF, DOCUMENT
    }
}
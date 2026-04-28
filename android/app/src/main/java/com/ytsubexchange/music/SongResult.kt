package com.ytsubexchange.music

data class SongResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationMs: Long = 0L
)

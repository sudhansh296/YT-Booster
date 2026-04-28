package com.ytsubexchange.music

import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Exact same logic as MusiQFlow Lite's YouTubeInnerTube.kt
object MusiqFlowYouTube {

    // ANDROID_VR clients return direct URLs that don't need n-transform
    // They work without cipher deobfuscation - same order as MusiQFlow Lite
    private val FALLBACK_CLIENTS = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_VR_NO_AUTH,
        IOS,
    )

    suspend fun search(query: String): Pair<List<SongResult>, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
            val songs = result.items.filterIsInstance<SongItem>().map { song ->
                SongResult(
                    videoId = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.thumbnail,
                    durationMs = (song.duration ?: 0) * 1000L
                )
            }
            songs to result.continuation
        }.getOrDefault(emptyList<SongResult>() to null)
    }

    suspend fun searchContinuation(continuation: String): Pair<List<SongResult>, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.searchContinuation(continuation).getOrThrow()
            val songs = result.items.filterIsInstance<SongItem>().map { song ->
                SongResult(
                    videoId = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.thumbnail,
                    durationMs = (song.duration ?: 0) * 1000L
                )
            }
            songs to result.continuation
        }.getOrDefault(emptyList<SongResult>() to null)
    }

    // Exact same as MusiQFlow Lite - NewPipe first, then direct URL
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val sigTimestamp = runCatching {
                NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
            }.getOrNull()

            val clients = listOf(WEB_REMIX) + FALLBACK_CLIENTS.toList()
            for (client in clients) {
                val playerResponse = runCatching {
                    YouTube.player(videoId = videoId, client = client, signatureTimestamp = sigTimestamp).getOrNull()
                }.getOrNull() ?: continue

                if (playerResponse.playabilityStatus.status != "OK") continue

                // NewPipe first (handles cipher/throttle), then direct URL
                val newPipeUrl = tryNewPipeStreamUrl(videoId, playerResponse)
                if (newPipeUrl != null) return@runCatching newPipeUrl

                val directUrl = findBestAudioUrl(playerResponse)
                if (directUrl != null) return@runCatching directUrl
            }
            null
        }.getOrNull()
    }


    private fun tryNewPipeStreamUrl(videoId: String, playerResponse: PlayerResponse): String? {
        return runCatching {
            val newPipeStreams = NewPipeExtractor.newPipePlayer(videoId)
            if (newPipeStreams.isEmpty()) return null

            val bestFormat = playerResponse.streamingData?.adaptiveFormats
                ?.filter { it.mimeType.startsWith("audio/") }
                ?.maxByOrNull { it.bitrate }

            if (bestFormat != null) {
                val url = newPipeStreams.find { it.first == bestFormat.itag }?.second
                if (url != null) return url
            }

            val cipherFormat = playerResponse.streamingData?.adaptiveFormats
                ?.filter { it.mimeType.startsWith("audio/") && it.signatureCipher != null }
                ?.maxByOrNull { it.bitrate }

            cipherFormat?.let { NewPipeExtractor.getStreamUrl(it, videoId) }
        }.getOrNull()
    }

    private fun findBestAudioUrl(playerResponse: PlayerResponse): String? {
        return playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.mimeType.startsWith("audio/") && it.url != null }
            ?.maxByOrNull { it.bitrate }
            ?.url
    }

    suspend fun getArtistThumbnail(artistName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            result?.items?.filterIsInstance<ArtistItem>()
                ?.firstOrNull { it.title.equals(artistName, ignoreCase = true) }?.thumbnail
                ?: result?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()?.thumbnail
        }.getOrNull()
    }

    suspend fun getLyrics(videoId: String, title: String, artist: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title
                .replace(Regex("\\s*\\(.*?\\)"), "")
                .replace(Regex("\\s*\\[.*?\\]"), "")
                .replace(Regex("\\s*feat\\..*$", RegexOption.IGNORE_CASE), "")
                .trim()
            val cleanArtist = artist.split(",", "&", "feat.", "ft.").first().trim()

            val url = "https://lrclib.net/api/search?track_name=${
                java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            }&artist_name=${
                java.net.URLEncoder.encode(cleanArtist, "UTF-8")
            }"

            val response = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder().url(url).addHeader("User-Agent", "MusiqFlow/1.0").build()
            ).execute()

            val body = response.body?.string() ?: return@runCatching null
            val json = org.json.JSONArray(body)
            if (json.length() == 0) return@runCatching null

            for (i in 0 until json.length()) {
                val synced = json.getJSONObject(i).optString("syncedLyrics", "")
                if (synced.isNotBlank()) return@runCatching synced
            }
            json.getJSONObject(0).optString("plainLyrics", null)
        }.getOrNull()
    }
}

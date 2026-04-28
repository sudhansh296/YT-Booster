package com.ytsubexchange.music

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    val searchResults   = MutableStateFlow<List<SongResult>>(emptyList())
    val isSearching     = MutableStateFlow(false)
    val isLoadingMore   = MutableStateFlow(false)
    val currentSong     = MutableStateFlow<SongResult?>(null)
    val isPlaying       = MutableStateFlow(false)
    val isLoading       = MutableStateFlow(false)
    val progress        = MutableStateFlow(0f)
    val currentPositionMs = MutableStateFlow(0L)
    val durationMs      = MutableStateFlow(0L)
    val statusText      = MutableStateFlow("")
    val searchHistory   = MutableStateFlow<List<String>>(emptyList())
    val lyrics          = MutableStateFlow<String?>(null)
    val isLoadingLyrics = MutableStateFlow(false)
    val parsedLyrics    = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val artistThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())

    private val currentQueue = MutableStateFlow<List<SongResult>>(emptyList())
    private val streamCache  = mutableMapOf<String, String>()
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var serviceConnected = false

    // ── Service Connection ────────────────────────────────────────────────────

    fun connectService() {
        if (serviceConnected && controller != null) return
        android.util.Log.d("MusicVM", "connectService()")
        try {
            val ctx = getApplication<Application>()

            MusicService.onNextRequested     = { playNext() }
            MusicService.onPreviousRequested = { playPrev() }

            val token = SessionToken(ctx, ComponentName(ctx, MusicService::class.java))
            controllerFuture = MediaController.Builder(ctx, token).buildAsync()
            controllerFuture?.addListener({
                try {
                    controller = controllerFuture?.get()
                    serviceConnected = true
                    android.util.Log.d("MusicVM", "Controller connected ✓")
                    attachPlayerListener()
                    startProgressUpdater()
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Controller connect failed", e)
                    serviceConnected = false
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            android.util.Log.e("MusicVM", "connectService failed", e)
        }
    }

    private fun attachPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                android.util.Log.d("MusicVM", "isPlaying=$playing")
                isPlaying.value = playing
                if (playing) {
                    isLoading.value = false
                    statusText.value = "Playing ♪"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                android.util.Log.d("MusicVM", "playbackState=$state")
                when (state) {
                    Player.STATE_READY -> {
                        durationMs.value = controller?.duration?.takeIf { it > 0 } ?: 0L
                        isLoading.value  = false
                        statusText.value = "Playing ♪"
                    }
                    Player.STATE_BUFFERING -> {
                        isLoading.value  = true
                        statusText.value = "Buffering..."
                    }
                    Player.STATE_ENDED -> {
                        isLoading.value  = false
                        isPlaying.value  = false
                        statusText.value = "Track ended"
                        // Use a background-safe coroutine scope instead of viewModelScope
                        // viewModelScope can be suspended when screen is off
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            kotlinx.coroutines.delay(300)
                            playNext()
                        }
                    }
                    Player.STATE_IDLE -> {
                        isLoading.value  = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("MusicVM", "Player error: ${error.message}", error)
                isLoading.value  = false
                isPlaying.value  = false
                statusText.value = "Playback error"
            }
        })
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        val pos = c.currentPosition
                        val dur = c.duration.takeIf { it > 0 } ?: 1L
                        currentPositionMs.value = pos
                        durationMs.value        = dur
                        progress.value          = pos.toFloat() / dur.toFloat()
                    }
                }
                delay(500)
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(query: String) {
        if (query.isBlank()) return
        addToHistory(query)
        viewModelScope.launch {
            isSearching.value     = true
            searchResults.value   = emptyList()
            val (results, cont)   = MusiqFlowYouTube.search(query)
            searchResults.value   = results
            _searchContinuation   = cont
            if (results.isNotEmpty()) {
                currentQueue.value = results
            }
            isSearching.value = false
        }
    }

    private var _searchContinuation: String? = null

    fun loadMoreResults() {
        val cont = _searchContinuation ?: return
        if (isLoadingMore.value) return
        viewModelScope.launch {
            isLoadingMore.value = true
            val (more, next)    = MusiqFlowYouTube.searchContinuation(cont)
            if (more.isNotEmpty()) {
                searchResults.value  = searchResults.value + more
                currentQueue.value   = searchResults.value
            }
            _searchContinuation = next
            isLoadingMore.value = false
        }
    }

    fun clearSearch() {
        searchResults.value = emptyList()
        isSearching.value   = false
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun playSong(song: SongResult) {
        android.util.Log.d("MusicVM", "playSong: ${song.title}")
        currentSong.value    = song
        isLoading.value      = true
        isPlaying.value      = false
        statusText.value     = "Loading..."
        lyrics.value         = null
        parsedLyrics.value   = emptyList()

        // Use GlobalScope so playback continues in background even when screen is off
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            // Ensure controller is ready
            if (controller == null) {
                android.util.Log.d("MusicVM", "Controller null, reconnecting...")
                connectService()
                var waited = 0
                while (controller == null && waited < 6000) {
                    kotlinx.coroutines.delay(200)
                    waited += 200
                }
            }

            val c = controller
            if (c == null) {
                android.util.Log.e("MusicVM", "Controller still null after wait!")
                statusText.value = "Service not ready"
                isLoading.value  = false
                return@launch
            }

            // Fetch stream URL
            statusText.value = "Fetching stream..."
            val url = streamCache[song.videoId] ?: run {
                val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MusiqFlowYouTube.getStreamUrl(song.videoId)
                }
                if (fetched != null) streamCache[song.videoId] = fetched
                fetched
            }

            if (url == null) {
                android.util.Log.e("MusicVM", "Stream URL null for ${song.videoId}")
                statusText.value = "Stream unavailable"
                isLoading.value  = false
                return@launch
            }

            android.util.Log.d("MusicVM", "Stream URL ready (${url.length} chars), playing...")
            statusText.value = "Starting..."

            try {
                c.stop()
                c.clearMediaItems()
                c.setMediaItem(MediaItem.fromUri(url))
                c.prepare()
                c.play()
                android.util.Log.d("MusicVM", "play() called ✓  state=${c.playbackState}")
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "play() failed", e)
                statusText.value = "Playback error"
                isLoading.value  = false
            }
        }

        // Load lyrics in parallel
        viewModelScope.launch {
            try {
                isLoadingLyrics.value = true
                val raw = MusiqFlowYouTube.getLyrics(song.videoId, song.title, song.artist)
                lyrics.value       = raw
                parsedLyrics.value = parseLyrics(raw)
            } catch (_: Exception) {
            } finally {
                isLoadingLyrics.value = false
            }
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(f: Float)   { controller?.seekTo((f * durationMs.value).toLong()) }
    fun seekForward()      { controller?.seekTo((controller?.currentPosition ?: 0) + 10_000) }
    fun seekBack()         { controller?.seekTo(maxOf(0, (controller?.currentPosition ?: 0) - 10_000)) }

    fun playNext() {
        val list = currentQueue.value; val cur = currentSong.value ?: return
        val idx  = list.indexOfFirst { it.videoId == cur.videoId }
        if (idx in 0 until list.size - 1) playSong(list[idx + 1])
    }

    fun playPrev() {
        val list = currentQueue.value; val cur = currentSong.value ?: return
        val idx  = list.indexOfFirst { it.videoId == cur.videoId }
        if (idx > 0) playSong(list[idx - 1])
    }

    fun fetchArtistThumbnail(artistName: String) {
        if (artistThumbnails.value.containsKey(artistName)) return
        viewModelScope.launch {
            val url = MusiqFlowYouTube.getArtistThumbnail(artistName) ?: return@launch
            artistThumbnails.value = artistThumbnails.value + (artistName to url)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseLyrics(raw: String?): List<Pair<Long, String>> {
        if (raw == null) return emptyList()
        val regex = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})\](.*)""")
        return raw.lines().mapNotNull { line ->
            val m   = regex.find(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val ms  = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val txt = m.groupValues[4].trim()
            if (txt.isEmpty()) return@mapNotNull null
            (min * 60_000L + sec * 1_000L + ms) to txt
        }.sortedBy { it.first }
    }

    private fun addToHistory(q: String) {
        val h = searchHistory.value.toMutableList()
        h.removeAll { it.equals(q, ignoreCase = true) }
        h.add(0, q)
        searchHistory.value = h.take(20)
    }

    override fun onCleared() {
        MusicService.onNextRequested     = null
        MusicService.onPreviousRequested = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

@file:Suppress("DEPRECATION")

package com.ytsubexchange.music.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.music.MusicViewModel
import com.ytsubexchange.music.SongResult

// ── Colors ────────────────────────────────────────────────────────────────────
val BgColor = Color(0xFF0D0D1A)
val AccentPurple = Color(0xFF7C3AED)
val AccentPink = Color(0xFFDB2777)
val SurfaceColor = Color(0xFF1A1A2E)
val TextPrimary = Color.White
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF4B5563)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(viewModel: MusicViewModel) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val history by viewModel.searchHistory.collectAsState()

    var showPlayer by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    // Auto-play first result after voice search
    var autoPlayPending by remember { mutableStateOf(false) }
    LaunchedEffect(searchResults, autoPlayPending) {
        if (autoPlayPending && searchResults.isNotEmpty()) {
            viewModel.playSong(searchResults.first())
            autoPlayPending = false
        }
    }

    // Voice search launcher
    var isListening by remember { mutableStateOf(false) }
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val query = matches?.firstOrNull()
            if (!query.isNullOrBlank()) {
                inputText = query
                autoPlayPending = true
                viewModel.search(query)
            }
        }
    }

    fun launchVoice() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Song ka naam bolo...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        voiceLauncher.launch(intent)
    }

    LaunchedEffect(currentSong) {
        // Song play hone pe player screen auto-open mat karo
        // Sirf mini player neeche dikhega
    }

    // Back press: player open hai to close karo, results hain to clear karo
    BackHandler(enabled = showPlayer || searchResults.isNotEmpty()) {
        when {
            showPlayer -> showPlayer = false
            searchResults.isNotEmpty() -> viewModel.clearSearch()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BgColor).systemBarsPadding()
    ) {
        // Floating music notes background
        FloatingMusicNotes()

        if (showPlayer && currentSong != null) {
            PlayerScreen(viewModel = viewModel, onBack = { showPlayer = false })
        } else {
            Column(modifier = Modifier.fillMaxSize()) {

                // Brand
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("♫", fontSize = 28.sp, color = AccentPurple)
                    Spacer(Modifier.width(8.dp))
                    Text("Musiq", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text("Flow", fontSize = 22.sp, fontWeight = FontWeight.Black, color = AccentPurple)
                }

                // Search bar + Mic button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceColor)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicSearchField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.search(inputText)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // Mic button
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isListening)
                                    Brush.linearGradient(listOf(AccentPink, Color(0xFFFF6B6B)))
                                else
                                    Brush.linearGradient(listOf(AccentPurple, AccentPink))
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { launchVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isListening) {
                            val pulse by rememberInfiniteTransition(label = "mp").animateFloat(
                                initialValue = 0.8f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                                label = "mp2"
                            )
                            Text("🎤", fontSize = (14 * pulse).sp)
                        } else {
                            Text("🎤", fontSize = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Show last searched query as chip if available
                if (inputText.isNotEmpty() && searchResults.isEmpty() && !isSearching) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentPurple.copy(alpha = 0.2f))
                                .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🔍 $inputText", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                // Main content — mini player height ka padding neeche
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isSearching -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AccentPurple)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Searching...", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                        searchResults.isNotEmpty() -> {
                            val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                            Column {
                                Text(
                                    "Results (${searchResults.size})",
                                    color = TextMuted, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 12.dp, end = 12.dp, top = 4.dp,
                                        bottom = if (currentSong != null) 80.dp else 4.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(searchResults) { song ->
                                        SongItem(
                                            song = song,
                                            isActive = currentSong?.videoId == song.videoId,
                                            onClick = { viewModel.playSong(song) }
                                        )
                                    }
                                    item {
                                        LaunchedEffect(searchResults.size) {
                                            viewModel.loadMoreResults()
                                        }
                                        if (isLoadingMore) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = AccentPurple,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            IdleContent(
                                history = history,
                                onArtistClick = { artist ->
                                    inputText = artist
                                    autoPlayPending = true
                                    viewModel.search(artist)
                                },
                                onHistoryClick = { q ->
                                    inputText = q
                                    autoPlayPending = true
                                    viewModel.search(q)
                                },
                                onOpenUrl = { url -> uriHandler.openUri(url) },
                                onVoiceSearch = { launchVoice() },
                                isListening = isListening,
                                viewModel = viewModel,
                                hasMiniPlayer = currentSong != null
                            )
                        }
                    }
                }
            }

            // Mini player — hamesha neeche, content ke upar
            if (currentSong != null) {
                MiniPlayerBar(
                    song = currentSong!!,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    onPlayPause = { viewModel.playPause() },
                    onExpand = { showPlayer = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun BasicSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text("Search songs, artists...", color = TextMuted, fontSize = 14.sp)
            }
            inner()
        }
    )
}

@Composable
fun SongItem(song: SongResult, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) AccentPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (isActive) AccentPurple.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isActive) AccentPurple else TextPrimary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (song.artist.isNotEmpty()) {
                Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (isActive) Text("▶", color = AccentPurple, fontSize = 14.sp)
    }
}

@Composable
fun IdleContent(
    history: List<String>,
    onArtistClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    isListening: Boolean = false,
    viewModel: MusicViewModel? = null,
    hasMiniPlayer: Boolean = false
) {
    val indianArtists = listOf("Arijit Singh", "Shreya Ghoshal", "AP Dhillon", "Diljit Dosanjh",
        "Jubin Nautiyal", "Neha Kakkar", "Atif Aslam", "Badshah")
    val intlArtists = listOf("The Weeknd", "Ed Sheeran", "Taylor Swift", "Drake",
        "Billie Eilish", "Bruno Mars", "Eminem", "Coldplay")

    val song = viewModel?.currentSong?.collectAsState()?.value
    val parsedLyr = viewModel?.parsedLyrics?.collectAsState()?.value ?: emptyList()
    val loadingLyr = viewModel?.isLoadingLyrics?.collectAsState()?.value ?: false
    val posMs = viewModel?.currentPositionMs?.collectAsState()?.value ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // History chips
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                history.take(6).forEach { q ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(SurfaceColor).clickable { onHistoryClick(q) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(q, color = TextSecondary, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text("🇮🇳 Indian Artists", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ArtistGrid(indianArtists, onArtistClick, viewModel)

        Spacer(Modifier.height(10.dp))

        Text("🌍 International", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ArtistGrid(intlArtists, onArtistClick, viewModel)

        Spacer(Modifier.height(16.dp))

        // Lyrics / Branding — normal height (no weight)
        if (song != null) {
            SyncedLyricsCard(
                songTitle = song.title,
                parsedLyrics = parsedLyr,
                isLoading = loadingLyr,
                currentPositionMs = posMs
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("♫", fontSize = 96.sp, color = AccentPurple.copy(alpha = 0.35f))
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("Musiq", color = TextPrimary.copy(alpha = 0.3f), fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Flow", color = AccentPurple.copy(alpha = 0.6f), fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(6.dp))
                Text("No Ads  •  Non Stop Music", color = TextMuted.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Social footer — visible on scroll
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(bottom = if (hasMiniPlayer) 80.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialBtn("Telegram", Color(0xFF229ED9), "✈", "https://t.me/free_telegram_bots_mod", Modifier.weight(1f), onOpenUrl)
            SocialBtn("Instagram", Color(0xFFE1306C), "📷", "https://www.instagram.com/coder_lobby", Modifier.weight(1f), onOpenUrl)
            SocialBtn("YouTube", Color(0xFFFF0000), "▶", "https://youtube.com/@coder_lobby", Modifier.weight(1f), onOpenUrl)
        }
    }
}

@Composable
fun ArtistGrid(artists: List<String>, onClick: (String) -> Unit, viewModel: MusicViewModel? = null) {
    val gradients = listOf(
        listOf(Color(0xFF7C3AED), Color(0xFFDB2777)),
        listOf(Color(0xFF0369A1), Color(0xFF7C3AED)),
        listOf(Color(0xFFBE185D), Color(0xFF7C3AED)),
        listOf(Color(0xFF6D28D9), Color(0xFF0369A1)),
        listOf(Color(0xFF9D174D), Color(0xFF6D28D9)),
        listOf(Color(0xFF1D4ED8), Color(0xFFDB2777)),
        listOf(Color(0xFFB45309), Color(0xFF7C3AED)),
        listOf(Color(0xFF0F766E), Color(0xFF1D4ED8))
    )

    val thumbnails by (viewModel?.artistThumbnails ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, String>())).collectAsState()

    // Trigger fetch for all artists
    LaunchedEffect(artists) {
        artists.forEach { viewModel?.fetchArtistThumbnail(it) }
    }

    val rows = artists.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { artist ->
                    val gi = artists.indexOf(artist) % gradients.size
                    val thumbUrl = thumbnails[artist]
                    Column(
                        modifier = Modifier.weight(1f).clickable { onClick(artist) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(Brush.linearGradient(gradients[gi])),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbUrl != null) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = artist,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(artist.first().toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(artist.split(" ").first(), color = TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: SongResult, isPlaying: Boolean, isLoading: Boolean,
    onPlayPause: () -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(16.dp)).background(SurfaceColor)
            .border(1.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onExpand).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.thumbnailUrl, contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AccentPurple, strokeWidth = 2.dp)
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentPurple, AccentPink)))
                    .clickable(
                        onClick = onPlayPause,
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun SocialBtn(label: String, color: Color, emoji: String, url: String, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onOpen(url) }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 14.sp, color = color)
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FloatingMusicNotes() {
    val notes = listOf("♪", "♫", "♩", "♬", "♪", "♫", "♩", "♬")
    val xPositions = listOf(0.08f, 0.25f, 0.45f, 0.65f, 0.80f, 0.15f, 0.55f, 0.90f)
    val durations = listOf(12000, 16000, 11000, 18000, 13000, 15000, 10000, 19000)
    val fontSizes = listOf(24, 32, 20, 28, 22, 30, 18, 26)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val screenWidthDp = maxWidth

        notes.forEachIndexed { i, note ->
            val infiniteTransition = rememberInfiniteTransition(label = "n$i")

            // Y: neeche se upar (1.0 → -0.1)
            val progress by infiniteTransition.animateFloat(
                initialValue = 1.0f + (i * 0.15f),
                targetValue = -0.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durations[i], easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "p$i"
            )

            // Alpha: neeche visible, upar fade out
            val alpha = when {
                progress > 0.8f -> (1f - progress) / 0.2f * 0.3f
                progress < 0.2f -> progress / 0.2f * 0.3f
                else -> 0.25f
            }.coerceIn(0f, 0.3f)

            Text(
                text = note,
                fontSize = fontSizes[i].sp,
                color = AccentPurple.copy(alpha = alpha),
                modifier = Modifier
                    .offset(
                        x = screenWidthDp * xPositions[i],
                        y = maxHeight * progress
                    )
                    .rotate(if (i % 2 == 0) 20f else -20f)
            )
        }
    }
}

@Composable
fun SyncedLyricsCard(
    songTitle: String,
    parsedLyrics: List<Pair<Long, String>>,
    isLoading: Boolean,
    currentPositionMs: Long
) {
    // Find current active line index
    val activeIndex = if (parsedLyrics.isEmpty()) -1 else {
        var idx = 0
        for (i in parsedLyrics.indices) {
            if (parsedLyrics[i].first <= currentPositionMs) idx = i else break
        }
        idx
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceColor)
            .border(1.dp, AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("♪", color = AccentPurple, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                songTitle,
                color = TextPrimary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        when {
            isLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = AccentPurple, strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Loading lyrics...", color = TextMuted, fontSize = 12.sp)
                }
            }
            parsedLyrics.isEmpty() -> {
                Text("Lyrics not available", color = TextMuted, fontSize = 12.sp)
            }
            else -> {
                // Show 5 lines around active: 2 before, active, 2 after (exactly like MusiQFlow)
                val start = maxOf(0, activeIndex - 2)
                val end = minOf(parsedLyrics.size - 1, activeIndex + 4)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in start..end) {
                        val isActive = i == activeIndex
                        val alpha = when {
                            isActive -> 1f
                            i < activeIndex -> 0.3f
                            else -> 0.55f
                        }
                        Text(
                            text = parsedLyrics[i].second,
                            color = if (isActive) AccentPurple else TextSecondary.copy(alpha = alpha),
                            fontSize = if (isActive) 16.sp else 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 22.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


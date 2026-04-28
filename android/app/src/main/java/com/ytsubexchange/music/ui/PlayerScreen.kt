package com.ytsubexchange.music.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.ytsubexchange.music.MusicViewModel

@Composable
fun PlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPos by viewModel.currentPositionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotation.animateTo(
                targetValue = rotation.value + 3600f,
                animationSpec = infiniteRepeatable(
                    animation = tween(40000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else rotation.stop()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Full blurred background
        if (song?.thumbnailUrl?.isNotEmpty() == true) {
            AsyncImage(
                model = song!!.thumbnailUrl.replace("w60-h60", "w300-h300"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.4f)
            )
        }
        // Dark gradient overlay
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to BgColor.copy(alpha = 0.3f),
                    0.4f to BgColor.copy(alpha = 0.85f),
                    1f to BgColor
                )
            )
        )

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Text("←", color = Color.White, fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Album art with glow
            Box(contentAlignment = Alignment.Center) {
                // Outer glow
                Box(
                    modifier = Modifier.size(280.dp).clip(CircleShape)
                        .background(Brush.radialGradient(
                            listOf(AccentPurple.copy(alpha = 0.5f), Color.Transparent)
                        ))
                )
                // Rotating disc ring
                Box(
                    modifier = Modifier.size(250.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(
                            listOf(AccentPurple, AccentPink, Color(0xFF0EA5E9), AccentPurple)
                        ))
                        .rotate(rotation.value)
                )
                // Album art
                Box(
                    modifier = Modifier.size(238.dp).clip(CircleShape)
                        .background(BgColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (song?.thumbnailUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = song!!.thumbnailUrl.replace("w60-h60", "w300-h300"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(230.dp).clip(CircleShape)
                        )
                    } else {
                        Text("♪", fontSize = 72.sp, color = Color.White)
                    }
                }
                // Center hole
                Box(modifier = Modifier.size(20.dp).clip(CircleShape)
                    .background(BgColor.copy(alpha = 0.9f)))
            }

            Spacer(Modifier.height(24.dp))

            // Song info
            Text(
                text = song?.title ?: "—",
                color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = song?.artist ?: "—",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp, textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(28.dp))

            // Progress
            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableStateOf(0f) }
            val displayProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Slider(
                    value = displayProgress,
                    onValueChange = { isDragging = true; dragProgress = it },
                    onValueChangeFinished = { viewModel.seekTo(dragProgress); isDragging = false },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = AccentPurple,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPos), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlBtn("⏮", 50.dp) { viewModel.playPrev() }
                ControlBtn("⏪", 50.dp) { viewModel.seekBack() }

                // Main play button
                Box(
                    modifier = Modifier.size(76.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentPurple, AccentPink)))
                        .clickable(
                            onClick = { viewModel.playPause() },
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                    } else {
                        Text(if (isPlaying) "⏸" else "▶", fontSize = 30.sp, color = Color.White)
                    }
                }

                ControlBtn("⏩", 50.dp) { viewModel.seekForward() }
                ControlBtn("⏭", 50.dp) { viewModel.playNext() }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ControlBtn(emoji: String, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.38f).sp, color = Color.White)
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

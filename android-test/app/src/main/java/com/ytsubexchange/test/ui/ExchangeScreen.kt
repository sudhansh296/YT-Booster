package com.ytsubexchange.test.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.ui.theme.isDarkTheme
import com.ytsubexchange.test.viewmodel.MainViewModel
import kotlinx.coroutines.delay

// ── Video type config ─────────────────────────────────────────
data class VideoType(
    val label: String,
    val emoji: String,
    val maxSeconds: Int,
    val coinsPerWatch: Int,
    val watcherCount: Int,   // for Submit tab
    val submitCost: Int      // coins needed to submit
)

val SHORT_VIDEO = VideoType("Short", "⚡", 60, 2, 50, 100)
val LONG_VIDEO  = VideoType("Long",  "🎬", 150, 5, 20, 300)

@Composable
fun ExchangeScreen(viewModel: MainViewModel) {
    val dark by isDarkTheme
    var selectedTab by remember { mutableStateOf(0) } // 0=Watch, 1=Submit

    Column(modifier = Modifier.fillMaxSize().background(AppColors.bg(dark))) {
        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.card(dark))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("👁 Watch & Earn", "📤 Submit Video").forEachIndexed { idx, label ->
                val selected = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFFFF0000) else AppColors.cardAlt(dark))
                        .clickable { selectedTab = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) Color.White else AppColors.textSecondary(dark),
                        fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        when (selectedTab) {
            0 -> WatchTab(viewModel, dark)
            1 -> SubmitTab(viewModel, dark)
        }
    }
}

// ── WATCH TAB ─────────────────────────────────────────────────
@Composable
fun WatchTab(viewModel: MainViewModel, dark: Boolean) {
    val profile by viewModel.profile.collectAsState()
    var videoType by remember { mutableStateOf(SHORT_VIDEO) }
    var watchState by remember { mutableStateOf<WatchState>(WatchState.Idle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Coins display
        profile?.let {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.card(dark))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("💰 ${it.coins} Coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Watch karke earn karo", color = AppColors.textSecondary(dark), fontSize = 12.sp)
            }
        }

        // Short / Long toggle
        Text("Video Type:", color = AppColors.text(dark), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(SHORT_VIDEO, LONG_VIDEO).forEach { vt ->
                val sel = videoType == vt
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Color(0xFFFF0000).copy(alpha = 0.15f) else AppColors.card(dark))
                        .clickable { videoType = vt; watchState = WatchState.Idle }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(vt.emoji, fontSize = 24.sp)
                    Text(vt.label, color = if (sel) Color(0xFFFF0000) else AppColors.text(dark),
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    Text("Max ${vt.maxSeconds}s", color = AppColors.textSecondary(dark), fontSize = 11.sp)
                    Text("+${vt.coinsPerWatch} coins", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Watch state UI
        when (val ws = watchState) {
            is WatchState.Idle -> {
                WatchVideoCard(videoType = videoType, dark = dark, onStart = { watchState = WatchState.Watching(videoType.maxSeconds) })
            }
            is WatchState.Watching -> {
                WatchingCard(
                    videoType = videoType,
                    dark = dark,
                    onComplete = {
                        viewModel.submitWatchReward(videoType.coinsPerWatch) { success, msg ->
                            watchState = if (success) WatchState.Done(videoType.coinsPerWatch) else WatchState.Idle
                        }
                    }
                )
            }
            is WatchState.Done -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A1A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎉 +${ws.earned} Coins Mila!", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { watchState = WatchState.Idle; viewModel.loadProfile() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) {
                            Text("Watch More")
                        }
                    }
                }
            }
        }
    }
}

sealed class WatchState {
    object Idle : WatchState()
    data class Watching(val totalSeconds: Int) : WatchState()
    data class Done(val earned: Int) : WatchState()
}

@Composable
fun WatchVideoCard(videoType: VideoType, dark: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current
    // Dummy video URL for testing
    val videoUrl = if (videoType == SHORT_VIDEO)
        "https://www.youtube.com/shorts/dQw4w9WgXcQ"
    else
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFF0000).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Text(videoType.emoji, fontSize = 22.sp)
                }
                Column {
                    Text("${videoType.label} Video", color = AppColors.text(dark), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Watch ${videoType.maxSeconds}s → +${videoType.coinsPerWatch} coins", color = Color(0xFFFFD700), fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                        onStart()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("▶ Watch Now", color = Color.White, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun WatchingCard(videoType: VideoType, dark: Boolean, onComplete: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(videoType.maxSeconds) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in videoType.maxSeconds downTo 1) {
            secondsLeft = i
            delay(1000)
        }
        completed = true
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${videoType.emoji} Watching ${videoType.label} Video...", color = AppColors.text(dark), fontWeight = FontWeight.Bold)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    progress = 1f - (secondsLeft.toFloat() / videoType.maxSeconds),
                    color = Color(0xFFFF0000),
                    trackColor = AppColors.cardAlt(dark),
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(80.dp)
                )
                Text("$secondsLeft", color = AppColors.text(dark), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Text("${videoType.maxSeconds - secondsLeft}/${videoType.maxSeconds}s watched",
                color = AppColors.textSecondary(dark), fontSize = 12.sp)

            if (completed) {
                Button(onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("✅ Claim +${videoType.coinsPerWatch} Coins", color = Color.White)
                }
            }
        }
    }
}

// ── SUBMIT TAB ────────────────────────────────────────────────
@Composable
fun SubmitTab(viewModel: MainViewModel, dark: Boolean) {
    val profile by viewModel.profile.collectAsState()
    var videoType by remember { mutableStateOf(SHORT_VIDEO) }
    var videoUrl by remember { mutableStateOf("") }
    var submitStatus by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Coins display
        profile?.let {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.card(dark))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("💰 ${it.coins} Coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Video submit karo, views pao", color = AppColors.textSecondary(dark), fontSize = 12.sp)
            }
        }

        // Short / Long toggle
        Text("Video Type:", color = AppColors.text(dark), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(SHORT_VIDEO, LONG_VIDEO).forEach { vt ->
                val sel = videoType == vt
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Color(0xFFFF0000).copy(alpha = 0.15f) else AppColors.card(dark))
                        .clickable { videoType = vt; submitStatus = "" }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(vt.emoji, fontSize = 24.sp)
                    Text(vt.label, color = if (sel) Color(0xFFFF0000) else AppColors.text(dark),
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    Text("${vt.watcherCount} watchers", color = AppColors.textSecondary(dark), fontSize = 11.sp)
                    Text("${vt.submitCost} coins", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Info card
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📋 ${videoType.label} Video Details", color = AppColors.text(dark), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Max duration:", color = AppColors.textSecondary(dark), fontSize = 12.sp)
                    Text("${videoType.maxSeconds}s", color = AppColors.text(dark), fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Watchers:", color = AppColors.textSecondary(dark), fontSize = 12.sp)
                    Text("~${videoType.watcherCount} users", color = AppColors.text(dark), fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cost:", color = AppColors.textSecondary(dark), fontSize = 12.sp)
                    Text("${videoType.submitCost} coins", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // URL input
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            label = { Text("YouTube Video URL", color = AppColors.textSecondary(dark)) },
            placeholder = { Text("https://youtube.com/...", color = AppColors.textSecondary(dark), fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF0000),
                unfocusedBorderColor = AppColors.cardAlt(dark),
                focusedTextColor = AppColors.text(dark),
                unfocusedTextColor = AppColors.text(dark),
                cursorColor = Color(0xFFFF0000)
            ),
            singleLine = true
        )

        if (submitStatus.isNotEmpty()) {
            Text(submitStatus,
                color = if (submitStatus.contains("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                fontSize = 13.sp)
        }

        val hasEnough = (profile?.coins ?: 0) >= videoType.submitCost
        Button(
            onClick = {
                if (videoUrl.isBlank()) { submitStatus = "❌ Video URL daalo"; return@Button }
                if (!videoUrl.contains("youtube.com") && !videoUrl.contains("youtu.be")) {
                    submitStatus = "❌ Valid YouTube URL daalo"; return@Button
                }
                isLoading = true
                submitStatus = "Submitting..."
                viewModel.submitVideoOrder(videoUrl.trim(), videoType.submitCost, videoType.label) { success, msg ->
                    isLoading = false
                    submitStatus = if (success) "✅ $msg" else "❌ $msg"
                    if (success) { videoUrl = ""; viewModel.loadProfile() }
                }
            },
            enabled = hasEnough && !isLoading && videoUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF0000),
                disabledContainerColor = AppColors.cardAlt(dark)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(
                if (hasEnough) "🚀 Submit (${videoType.submitCost} coins)" else "❌ Need ${videoType.submitCost} coins",
                color = Color.White, fontSize = 14.sp
            )
        }
    }
}

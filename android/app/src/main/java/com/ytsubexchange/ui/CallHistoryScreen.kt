package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.data.CallLogEntry
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel

@Composable
fun CallHistoryScreen(viewModel: MainViewModel, onBack: () -> Unit = {}) {
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSec = AppColors.textSecondary(dark)
    val callHistory by viewModel.callHistory.collectAsState()
    val isLoading by viewModel.isLoadingCallHistory.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadCallHistory() }

    Column(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = textColor) }
            Text("Call History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF0000))
            }
        } else if (callHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📞", fontSize = 40.sp)
                    Text("Koi call history nahi", color = textSec, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(callHistory) { call ->
                    CallHistoryItem(call = call, card = card, textColor = textColor, textSec = textSec)
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(call: CallLogEntry, card: Color, textColor: Color, textSec: Color) {
    val isVideo = call.callType == "video"
    val isMissed = call.isMissed || call.status == "missed"
    val isDeclined = call.isDeclined || call.status == "declined"
    val isOutgoing = call.isOutgoing

    // Color based on call result
    val iconColor = when {
        isMissed && !isOutgoing -> Color(0xFFFF6B6B)   // missed incoming = red
        isDeclined -> Color(0xFFFF9800)                  // declined = orange
        isOutgoing -> Color(0xFF29B6F6)                  // outgoing = blue
        else -> Color(0xFF4CAF50)                        // connected = green
    }

    // Direction icon
    val directionIcon = when {
        isMissed && !isOutgoing -> Icons.Default.CallMissed
        isOutgoing -> Icons.Default.CallMade
        else -> Icons.Default.CallReceived
    }

    // Status text
    val statusText = when {
        isMissed -> "Missed"
        isDeclined -> "Declined"
        call.status == "connected" || call.status == "ended" -> formatDuration(call.duration)
        else -> call.status.replaceFirstChar { it.uppercase() }
    }

    // Direction label
    val directionLabel = when {
        isMissed && !isOutgoing -> "Missed"
        isOutgoing -> "Outgoing"
        else -> "Incoming"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Call type icon (voice/video)
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(iconColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Name + direction
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    call.callerName.ifEmpty { "Unknown" },
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        directionIcon,
                        null,
                        tint = iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "$directionLabel • ${if (isVideo) "Video" else "Voice"}",
                        color = iconColor,
                        fontSize = 12.sp
                    )
                }
            }

            // Right side: duration + date
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    statusText,
                    color = if (isMissed && !isOutgoing) Color(0xFFFF6B6B) else textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatCallDate(call.startTime),
                    color = textSec,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    return when {
        seconds <= 0 -> "0s"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val m = seconds / 60
            val s = seconds % 60
            if (s == 0) "${m}m" else "${m}m ${s}s"
        }
        else -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }
}

private fun formatCallDate(dateStr: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val date = sdf.parse(dateStr.take(19)) ?: return dateStr.take(10)
        val now = java.util.Date()
        val diff = now.time - date.time
        val days = diff / (1000 * 60 * 60 * 24)
        when {
            days == 0L -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
            days == 1L -> "Yesterday"
            days < 7 -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(date)
            else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        dateStr.take(10)
    }
}

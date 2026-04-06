package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel

@Composable
fun LeaderboardScreen(viewModel: MainViewModel) {
    val leaderboardData by viewModel.leaderboard.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)

    LaunchedEffect(Unit) {
        if (viewModel.leaderboard.value == null) viewModel.loadLeaderboard()
    }

    Column(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 Leaderboard", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        leaderboardData?.let { data ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1A1A3A) else Color(0xFFE8EAF6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Meri Rank", color = textSecondary, fontSize = 13.sp)
                    Text("#${data.myRank}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${data.myEarned} coins earned", color = Color(0xFF29B6F6), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(data.leaderboard) { index, entry ->
                    val rank = index + 1
                    val rankColor = when (rank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32); else -> textSecondary }
                    val rankEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (rank <= 3) (if (dark) Color(0xFF1A1A2A) else Color(0xFFEDE7F6)) else card),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(rankEmoji, fontSize = if (rank <= 3) 22.sp else 16.sp, color = rankColor, modifier = Modifier.width(36.dp))
                            AsyncImage(model = entry.profilePic, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.channelName, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                Text("Given: ${entry.subscribersGiven}", color = textSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${entry.totalEarned}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("coins", color = textSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚠️ Load nahi hua", color = Color(0xFFFF6B6B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(loadError ?: "Internet check karo", color = textSecondary, fontSize = 13.sp)
                    Button(
                        onClick = { viewModel.loadLeaderboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("🔄 Retry", color = Color.White) }
                }
            } else {
                CircularProgressIndicator(color = Color(0xFFFF0000))
            }
        }
    }
}

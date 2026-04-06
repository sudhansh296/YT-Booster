package com.ytsubexchange.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.ui.theme.isDarkTheme
import com.ytsubexchange.test.viewmodel.MainViewModel

@Composable
fun StreakScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val streak by viewModel.streak.collectAsState()
    var bonusMsg by remember { mutableStateOf("") }
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)

    LaunchedEffect(Unit) { viewModel.loadStreak() }

    Column(modifier = Modifier.fillMaxSize().background(bg)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
            }
            Text("🔥 Daily Streak", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔥", fontSize = 56.sp)
                    Text("${streak?.currentStreak ?: 0}", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 64.sp)
                    Text("Din ki Streak", color = textSecondary, fontSize = 16.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${streak?.longestStreak ?: 0}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("Best Streak", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+2", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("Daily Coins", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Streak Rewards", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val milestones = listOf(
                        Triple(7, "+5 Bonus Coins", (streak?.currentStreak ?: 0) >= 7),
                        Triple(14, "+5 Bonus Coins", (streak?.currentStreak ?: 0) >= 14),
                        Triple(30, "+5 Bonus Coins", (streak?.currentStreak ?: 0) >= 30),
                        Triple(60, "+5 Bonus Coins", (streak?.currentStreak ?: 0) >= 60),
                        Triple(100, "+10 Bonus Coins", (streak?.currentStreak ?: 0) >= 100)
                    )
                    milestones.forEach { (days, reward, achieved) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (achieved) "✅" else "⬜", fontSize = 16.sp)
                                Text("$days din", color = if (achieved) textColor else textSecondary, fontSize = 14.sp)
                            }
                            Text(reward, color = if (achieved) Color(0xFF4CAF50) else textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (bonusMsg.isNotEmpty()) {
                Text(bonusMsg, color = if (bonusMsg.contains("+")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), textAlign = TextAlign.Center)
            }
            Button(
                onClick = { viewModel.claimDailyBonus { success, msg -> bonusMsg = msg; if (success) viewModel.loadStreak() } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎁 Aaj ka Daily Bonus Claim Karo", color = Color.White, modifier = Modifier.padding(8.dp))
            }

            Text("Har 7 din pe extra +5 coins milte hain!", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

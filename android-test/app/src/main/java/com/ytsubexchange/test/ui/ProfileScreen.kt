package com.ytsubexchange.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.ui.theme.isDarkTheme
import com.ytsubexchange.test.viewmodel.MainViewModel

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateToStreak: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val referral by viewModel.referral.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Only reload if not already loaded (init already fetched these)
        if (viewModel.streak.value == null) viewModel.loadStreak()
        if (viewModel.referral.value == null) viewModel.loadReferral()
    }

    val dark by isDarkTheme
    val textColor = AppColors.text(dark)
    val card = AppColors.card(dark)
    val bg = AppColors.bg(dark)
    val textSecondary = AppColors.textSecondary(dark)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar - just title, no back button (it's a tab)
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("👤 Profile", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = onNavigateToSettings) {
                Text("⚙️", fontSize = 22.sp)
            }
        }

        profile?.let { p ->
            // Profile header
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Big profile pic
                AsyncImage(
                    model = p.profilePic,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                )
                Text(p.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(p.channelUrl, color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)

                // Coins badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF2A2000) else Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💰", fontSize = 20.sp)
                        Text("${p.coins} Coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            // Stats grid
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Subscriber stats
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Subscriber Stats", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ProfileStat("Subscribers\nDiye", p.subscribersGiven.toString(), Color(0xFF29B6F6))
                            ProfileStat("Subscribers\nMile", p.subscribersReceived.toString(), Color(0xFF4CAF50))
                            ProfileStat("Total\nEarned", "${p.totalEarned} 🪙", Color(0xFFFFD700))
                        }
                    }
                }

                // Streak card
                streak?.let { s ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToStreak() }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                ProfileStat("Current\nStreak", "🔥 ${s.currentStreak}", Color(0xFFFF6B00))
                                ProfileStat("Best\nStreak", "⭐ ${s.longestStreak}", Color(0xFFFFD700))
                            }
                            Text("›", color = Color.Gray, fontSize = 22.sp)
                        }
                    }
                }

                    // Referral card
                    referral?.let { r ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = card),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Referral", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                ProfileStat("Friends\nInvited", r.referralCount.toString(), Color(0xFF9C27B0))
                                ProfileStat("Referral\nCoins", r.referralEarned.toString(), Color(0xFFFFD700))
                                ProfileStat("My\nCode", r.referralCode, Color(0xFF29B6F6))
                            }
                        }
                    }
                }

                // Transaction History
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📋 Transaction History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        if (transactions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            transactions.take(3).forEach { txn ->
                                TransactionItem(txn = txn, dark = dark)
                            }
                            if (transactions.size >= 3) {
                                TextButton(
                                    onClick = onNavigateToHistory,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View All →", color = Color(0xFFFF0000), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // Logout button
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                            .edit().remove("token").apply()
                        // Restart app
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout", modifier = Modifier.padding(6.dp))
                }

                Spacer(Modifier.height(20.dp))
            }
        } ?: Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            if (loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚠️ Load nahi hua", color = Color(0xFFFF6B6B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(loadError ?: "Internet check karo aur retry karo", color = AppColors.textSecondary(dark), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = { viewModel.loadInit() },
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

@Composable
fun ProfileStat(label: String, value: String, valueColor: Color) {
    val dark by isDarkTheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        Text(label, color = AppColors.textSecondary(dark), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

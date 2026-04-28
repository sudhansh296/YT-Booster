package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateToStreak: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToBoost: () -> Unit = {},
    onNavigateToCallHistory: () -> Unit = {},
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val referral by viewModel.referral.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadTransactions(); viewModel.loadCallHistory() }
    val dark by isDarkTheme
    val textColor = AppColors.text(dark)
    val card = AppColors.card(dark)
    val bg = AppColors.bg(dark)
    val textSecondary = AppColors.textSecondary(dark)

    val authPrefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { "Bearer ${authPrefs.getString("token", "")}" }
    var boostStatus by remember { mutableStateOf<com.ytsubexchange.data.BoostStatusResponse?>(null) }
    LaunchedEffect(Unit) {
        try { boostStatus = RetrofitClient.api.getBoostStatus(token) } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Profile", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = onNavigateToSettings) { Text("\u2699\uFE0F", fontSize = 22.sp) }
        }

        profile?.let { p ->
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                    if (p.profilePic.isNotEmpty()) {
                        AsyncImage(model = p.profilePic, contentDescription = null, modifier = Modifier.size(96.dp).clip(CircleShape),
                            error = androidx.compose.ui.res.painterResource(com.ytsubexchange.R.drawable.subscribers_icon))
                    } else {
                        Text(p.channelName.take(1).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(p.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(p.channelUrl, color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
                Card(colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF2A2000) else Color(0xFFFFF8E1)), shape = RoundedCornerShape(20.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${p.coins} Coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Subscriber Stats", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ProfileStat("Subscribers\nDiye", p.subscribersGiven.toString(), Color(0xFF29B6F6))
                            ProfileStat("Subscribers\nMile", p.subscribersReceived.toString(), Color(0xFF4CAF50))
                            ProfileStat("Total\nEarned", "${p.totalEarned} coins", Color(0xFFFFD700))
                        }
                    }
                }

                streak?.let { s ->
                    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onNavigateToStreak() }) {
                        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF6B00).copy(0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6B00), modifier = Modifier.size(22.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    ProfileStat("Current\nStreak", "${s.currentStreak} days", Color(0xFFFF6B00))
                                    ProfileStat("Best\nStreak", "${s.longestStreak} days", Color(0xFFFFD700))
                                }
                            }
                            Text(">", color = Color.Gray, fontSize = 22.sp)
                        }
                    }
                }

                referral?.let { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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

                Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onNavigateToBoost() }) {
                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF7B2FF7).copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFF7B2FF7), modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Channel Boost", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(if (boostStatus?.isBoosted == true) "Boosted - Active" else "Coins se channel top pe dikhao", color = textSecondary, fontSize = 12.sp)
                            }
                        }
                        Text(">", color = Color.Gray, fontSize = 22.sp)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onNavigateToFriends() }) {
                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF29B6F6).copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Friends", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Friends dhundo, add karo, track karo", color = textSecondary, fontSize = 12.sp)
                            }
                        }
                        Text(">", color = Color.Gray, fontSize = 22.sp)
                    }
                }

                // Transaction History — 2 items
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Transaction History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (transactions.isNotEmpty()) {
                            Text("View All", color = Color(0xFFFF0000), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onNavigateToTransactions() })
                        }
                    }
                    if (transactions.isEmpty()) {
                        Text("Koi transaction nahi mila", color = textSecondary, fontSize = 13.sp)
                    } else {
                        transactions.take(2).forEach { txn -> TransactionItem(txn = txn, dark = dark) }
                    }
                }

                // Call History — 2 items
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Call History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (callHistory.isNotEmpty()) {
                            Text("View All", color = Color(0xFFFF0000), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onNavigateToCallHistory() })
                        }
                    }
                    if (callHistory.isEmpty()) {
                        Text("Koi call history nahi", color = textSecondary, fontSize = 13.sp)
                    } else {
                        callHistory.take(2).forEach { call ->
                            CallHistoryItem(call = call, card = card, textColor = textColor, textSec = textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        authPrefs.edit().clear().apply()
                        onLogout()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Logout", modifier = Modifier.padding(6.dp)) }

                Spacer(Modifier.height(20.dp))
            }
        } ?: Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            if (loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Load nahi hua", color = Color(0xFFFF6B6B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(loadError ?: "Internet check karo aur retry karo", color = AppColors.textSecondary(dark), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Button(onClick = { viewModel.loadInit() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)), shape = RoundedCornerShape(12.dp)) {
                        Text("Retry", color = Color.White)
                    }
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
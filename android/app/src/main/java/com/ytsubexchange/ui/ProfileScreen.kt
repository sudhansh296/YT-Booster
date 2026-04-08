package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateToStreak: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val referral by viewModel.referral.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val isLoadingTransactions by viewModel.isLoadingTransactions.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadTransactions() }

    val dark by isDarkTheme
    val textColor = AppColors.text(dark)
    val card = AppColors.card(dark)
    val bg = AppColors.bg(dark)
    val textSecondary = AppColors.textSecondary(dark)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
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
                Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                    if (p.profilePic.isNotEmpty()) {
                        AsyncImage(
                            model = p.profilePic,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                            error = androidx.compose.ui.res.painterResource(com.ytsubexchange.R.drawable.subscribers_icon)
                        )
                    } else {
                        Text(p.channelName.take(1).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
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

                // Channel Boost card
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                val authPrefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
                val token = remember { "Bearer ${authPrefs.getString("token", "")}" }
                var boostStatus by remember { mutableStateOf<com.ytsubexchange.data.BoostStatusResponse?>(null) }
                var showBoostDialog by remember { mutableStateOf(false) }
                var boostMsg by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    try { boostStatus = com.ytsubexchange.network.RetrofitClient.api.getBoostStatus(token) } catch (e: Exception) { }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showBoostDialog = true }
                ) {
                    Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("🚀 Channel Boost", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                if (boostStatus?.isBoosted == true) "Boosted ✅ — ${boostStatus?.boostedUntil?.take(16)?.replace("T"," ")}"
                                else "Coins se channel top pe dikhao",
                                color = AppColors.textSecondary(dark), fontSize = 12.sp
                            )
                        }
                        Text("›", color = Color.Gray, fontSize = 22.sp)
                    }
                }

                if (showBoostDialog) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showBoostDialog = false }) {
                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🚀 Channel Boost", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Apna channel featured section mein dikhao!", color = AppColors.textSecondary(dark), fontSize = 13.sp)
                                if (boostMsg.isNotEmpty()) Text(boostMsg, color = if (boostMsg.contains("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
                                listOf("6h" to 20, "12h" to 35, "24h" to 60, "48h" to 100).forEach { (dur, cost) ->
                                    Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .background(Color(0xFF7B2FF7).copy(0.15f))
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val resp = com.ytsubexchange.network.RetrofitClient.api.boostChannel(token, mapOf("duration" to dur))
                                                    boostMsg = "✅ Channel boosted for $dur! Coins: ${resp.coins}"
                                                    boostStatus = com.ytsubexchange.network.RetrofitClient.api.getBoostStatus(token)
                                                } catch (e: Exception) { boostMsg = "❌ ${e.message?.take(50)}" }
                                            }
                                        }.padding(14.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("$dur boost", color = textColor, fontWeight = FontWeight.Bold)
                                            Text("$cost 🪙", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                TextButton(onClick = { showBoostDialog = false }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Close", color = AppColors.textSecondary(dark))
                                }
                            }
                        }
                    }
                }
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📋 Transaction History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (transactions.isNotEmpty()) {
                                Text(
                                    "View All →",
                                    color = Color(0xFFFF0000),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onNavigateToTransactions() }
                                )
                            }
                        }
                        if (isLoadingTransactions) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else if (transactions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Koi transaction nahi mila", color = textSecondary, fontSize = 13.sp)
                            }
                        } else {
                            transactions.take(5).forEach { txn ->
                                TransactionItem(txn = txn, dark = dark)
                            }
                            if (transactions.size > 5) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF0000).copy(alpha = 0.1f))
                                        .clickable { onNavigateToTransactions() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Load More (${transactions.size - 5} remaining) →",
                                        color = Color(0xFFFF0000),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
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

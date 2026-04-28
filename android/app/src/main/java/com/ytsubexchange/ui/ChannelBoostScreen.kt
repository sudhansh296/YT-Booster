package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.data.BoostRequest
import com.ytsubexchange.data.BoostedChannel
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Composable
fun ChannelBoostScreen(onBack: () -> Unit = {}) {
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSec = AppColors.textSecondary(dark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { "Bearer ${prefs.getString("token", "")}" }

    var boostCoins by remember { mutableStateOf(0) }
    var isBoosted by remember { mutableStateOf(false) }
    var boostedUntil by remember { mutableStateOf("") }
    var boostedChannels by remember { mutableStateOf<List<BoostedChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMsg by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            isLoading = true
            try {
                val status = RetrofitClient.api.getBoostStatus(token)
                boostCoins = status.coins
                isBoosted = status.isBoosted
                boostedUntil = status.boostedUntil ?: ""
                val resp = RetrofitClient.api.getBoostedChannels(token)
                boostedChannels = resp.channels
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), Color(0xFF4A1A8A))))
                .padding(4.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Channel Boost", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Apna channel top pe dikhao", color = Color.White.copy(0.7f), fontSize = 12.sp)
            }
            Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(28.dp).padding(end = 8.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF7B2FF7))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status message
                if (statusMsg.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSuccess) Color(0xFF1A3A1A) else Color(0xFF3A1A1A))
                                .padding(14.dp)
                        ) {
                            Text(statusMsg, color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Current status card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isBoosted) Color(0xFF1A2A1A) else card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (isBoosted) Color(0xFF4CAF50).copy(0.2f) else Color(0xFF7B2FF7).copy(0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isBoosted) Icons.Default.Check else Icons.Default.Star,
                                    null,
                                    tint = if (isBoosted) Color(0xFF4CAF50) else Color(0xFF7B2FF7),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isBoosted) "Boost Active!" else "Boost Inactive",
                                    color = if (isBoosted) Color(0xFF4CAF50) else textColor,
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                                )
                                Text(
                                    if (isBoosted) "Until: ${boostedUntil.take(16).replace("T", " ")}"
                                    else "Boost karke top pe aao!",
                                    color = textSec, fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$boostCoins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("coins", color = textSec, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Section title
                item {
                    Text("Boost Duration Choose Karo", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Boost options
                val options = listOf(
                    Triple("2h", 20, "2 Ghante"),
                    Triple("6h", 35, "6 Ghante"),
                    Triple("12h", 60, "12 Ghante"),
                    Triple("24h", 100, "24 Ghante (1 Din)")
                )
                items(options) { (dur, cost, label) ->
                    val canAfford = boostCoins >= cost
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (canAfford)
                                    Brush.horizontalGradient(listOf(Color(0xFF7B2FF7).copy(0.15f), Color(0xFF4A1A8A).copy(0.1f)))
                                else
                                    Brush.horizontalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)))
                            )
                            .clickable(enabled = canAfford) {
                                scope.launch {
                                    statusMsg = ""
                                    try {
                                        val resp = RetrofitClient.api.boostChannel(token, BoostRequest(dur))
                                        isSuccess = true
                                        statusMsg = "Channel boosted for $label! Remaining: ${resp.coins} coins"
                                        reload()
                                    } catch (e: retrofit2.HttpException) {
                                        isSuccess = false
                                        val body = try { e.response()?.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                                        statusMsg = try { org.json.JSONObject(body).optString("error", "Error ${e.code()}") } catch (_: Exception) { "Error ${e.code()}" }
                                    } catch (e: Exception) {
                                        isSuccess = false
                                        statusMsg = "Error: ${e.message?.take(60)}"
                                    }
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (canAfford) Color(0xFF7B2FF7).copy(0.2f) else Color(0xFF333333)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FlashOn, null,
                                    tint = if (canAfford) Color(0xFF7B2FF7) else Color(0xFF666666),
                                    modifier = Modifier.size(22.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = if (canAfford) textColor else Color(0xFF666666), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (canAfford) "Tap to boost" else "Need $cost coins (you have $boostCoins)",
                                    color = if (canAfford) textSec else Color(0xFF555555), fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$cost", color = if (canAfford) Color(0xFFFFD700) else Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("coins", color = textSec, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Boosted channels section
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Text("Currently Boosted Channels", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                if (boostedChannels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(card).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Koi boosted channel nahi abhi", color = textSec, fontSize = 13.sp)
                                Text("Pehle boost karo!", color = textSec, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    items(boostedChannels) { ch ->
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
                                if (ch.profilePic.isNotEmpty()) {
                                    AsyncImage(model = ch.profilePic, contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(CircleShape))
                                } else {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF7B2FF7).copy(0.3f)),
                                        contentAlignment = Alignment.Center) {
                                        Text(ch.channelName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(ch.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF7B2FF7).copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("BOOSTED", color = Color(0xFF7B2FF7), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text("Until: ${ch.boostedUntil.take(16).replace("T", " ")}", color = textSec, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.Star, null, tint = Color(0xFF7B2FF7), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
package com.ytsubexchange.ui.ludo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LudoLobbyScreen(
    myCoins: Int,
    onBack: () -> Unit,
    onStartGame: (mode: LudoMode, playerCount: Int, roomCode: String?) -> Unit
) {
    var selectedMode by remember { mutableStateOf<LudoMode?>(null) }
    var playerCount by remember { mutableStateOf(4) }
    var roomCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var showJoinField by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF1A0A2E))))
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text("🎯 Ludo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            // Hero
            Box(
                Modifier.fillMaxWidth().padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎲", fontSize = 56.sp)
                    Text("Ludo King", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Coins jeeto, dosto ko harao!", color = Color.White.copy(0.8f), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Tumhare coins: 🪙 $myCoins", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mode selection
            Text("Game Mode Chuno", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            // VS Computer (free)
            ModeCard(
                emoji = "🤖",
                title = "vs Computer",
                subtitle = "Free • Practice mode • No coins",
                gradient = listOf(Color(0xFF1A3A1A), Color(0xFF0A2A0A)),
                borderColor = Color(0xFF4CAF50),
                badge = "FREE",
                badgeColor = Color(0xFF4CAF50),
                selected = selectedMode == LudoMode.VS_COMPUTER,
                onClick = { selectedMode = LudoMode.VS_COMPUTER; showJoinField = false }
            )

            Spacer(Modifier.height(8.dp))

            // Local multiplayer
            ModeCard(
                emoji = "👥",
                title = "Local Multiplayer",
                subtitle = "Ek phone pe 2-4 players • No coins",
                gradient = listOf(Color(0xFF1A1A3A), Color(0xFF0A0A2A)),
                borderColor = Color(0xFF29B6F6),
                badge = "LOCAL",
                badgeColor = Color(0xFF29B6F6),
                selected = selectedMode == LudoMode.LOCAL,
                onClick = { selectedMode = LudoMode.LOCAL; showJoinField = false }
            )

            Spacer(Modifier.height(8.dp))

            // Online P2P
            ModeCard(
                emoji = "🌐",
                title = "Online P2P",
                subtitle = "50 coins per player • Room code se khelo",
                gradient = listOf(Color(0xFF3A1A1A), Color(0xFF2A0A0A)),
                borderColor = Color(0xFFFFD700),
                badge = "50🪙",
                badgeColor = Color(0xFFFFD700),
                selected = selectedMode == LudoMode.ONLINE,
                onClick = { selectedMode = LudoMode.ONLINE }
            )

            // Player count (for non-online modes)
            if (selectedMode != null && selectedMode != LudoMode.ONLINE) {
                Spacer(Modifier.height(16.dp))
                Text("Players", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (playerCount == count) Color(0xFF7B2FF7) else Color(0xFF1A1A2E))
                                .clickable { playerCount = count }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("$count 👤", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Online options
            if (selectedMode == LudoMode.ONLINE) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Create room
                    val generatedCode = remember { (100000..999999).random().toString() }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1A2E))
                            .clickable {
                                roomCode = generatedCode
                                showJoinField = false
                                onStartGame(LudoMode.ONLINE, playerCount, generatedCode)
                            }
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("🏠 Room Banao", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Code: $generatedCode", color = Color(0xFFFFD700), fontSize = 13.sp)
                            Text("Dosto ko yeh code share karo", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    // Join room
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1A2E))
                            .clickable { showJoinField = !showJoinField }
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔗 Room Join Karo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (showJoinField) {
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0D0D1A))
                                        .padding(12.dp)
                                ) {
                                    if (joinCode.isEmpty()) Text("Room code daalo...", color = Color.Gray, fontSize = 14.sp)
                                    BasicTextField(
                                        value = joinCode,
                                        onValueChange = { if (it.length <= 6) joinCode = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (joinCode.length == 6) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                                            .clickable { onStartGame(LudoMode.ONLINE, playerCount, joinCode) }
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Join Room", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Player count for online
                    Text("Players", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(2, 4).forEach { count ->
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(if (playerCount == count) Color(0xFF7B2FF7) else Color(0xFF1A1A2E))
                                    .clickable { playerCount = count }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                val prize = if (count == 2) "1st=75🪙" else "1st=75, 2nd=50, 3rd=25🪙"
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$count 👤", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(prize, color = Color(0xFFFFD700), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Start button (for non-online modes)
            if (selectedMode != null && selectedMode != LudoMode.ONLINE) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                        .clickable { onStartGame(selectedMode!!, playerCount, null) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎮 Game Shuru Karo!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeCard(
    emoji: String, title: String, subtitle: String,
    gradient: List<Color>, borderColor: Color,
    badge: String, badgeColor: Color,
    selected: Boolean, onClick: () -> Unit
) {
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .then(if (selected) Modifier.background(borderColor.copy(alpha = 0.1f)) else Modifier)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (selected) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            ) {
                // Selected border effect
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                    .background(borderColor.copy(alpha = 0.15f)))
            }
        }
    }
}

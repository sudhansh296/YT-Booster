package com.ytsubexchange.ui.ludo

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LudoScreen(
    mode: LudoMode,
    playerCount: Int = 4,
    roomCode: String? = null,
    myCoins: Int = 0,
    onBack: () -> Unit,
    onGameEnd: (List<Pair<LudoColor, Int>>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Setup player types
    val playerTypes = when (mode) {
        LudoMode.VS_COMPUTER -> List(playerCount) { if (it == 0) PlayerType.HUMAN else PlayerType.COMPUTER }
        else -> List(playerCount) { PlayerType.HUMAN }
    }

    val engine = remember { LudoEngine(playerCount, playerTypes) }
    var gameState by remember { mutableStateOf(engine.state) }
    var diceRolling by remember { mutableStateOf(false) }
    var diceRotation by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }

    // Dice roll animation
    val diceAnim = rememberInfiniteTransition(label = "dice")
    val diceAngle by diceAnim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(300)),
        label = "roll"
    )

    // Auto computer move
    LaunchedEffect(gameState.currentPlayerIdx, gameState.diceRolled) {
        val player = gameState.currentPlayer
        if (player.type == PlayerType.COMPUTER && !gameState.isGameOver) {
            delay(800)
            if (!gameState.diceRolled) {
                diceRolling = true
                delay(600)
                val result = engine.computerMove()
                diceRolling = false
                gameState = engine.state
                if (result != null) {
                    delay(500)
                    engine.moveToken(result.first)
                    gameState = engine.state
                }
            }
        }
        if (gameState.isGameOver && !showResult) {
            delay(500)
            showResult = true
            val prizes = gameState.finishOrder.mapIndexed { idx, color ->
                Pair(color, engine.calculatePrize(playerCount, idx + 1))
            }
            onGameEnd(prizes)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0A00), Color(0xFF2D1500), Color(0xFF1A0A00))))
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text(
                    when (mode) {
                        LudoMode.VS_COMPUTER -> "🎮 Ludo vs Computer"
                        LudoMode.LOCAL -> "🎮 Local Multiplayer"
                        LudoMode.ONLINE -> "🎮 Online — Room: ${roomCode ?: ""}"
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                if (mode != LudoMode.VS_COMPUTER) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🪙 ${playerCount * 50}", color = Color(0xFFFFD700), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Player indicators
            PlayerIndicatorRow(gameState)

            // Board
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                LudoBoardView(
                    state = gameState,
                    onTokenClick = { color, tokenId ->
                        if (gameState.currentPlayer.color == color &&
                            gameState.currentPlayer.type == PlayerType.HUMAN &&
                            gameState.diceRolled) {
                            engine.moveToken(tokenId)
                            gameState = engine.state
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom controls
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF2D1500), Color(0xFF1A0A00))))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status message
                Text(
                    gameState.message.ifEmpty {
                        if (gameState.diceRolled) "Token choose karo"
                        else "${gameState.currentPlayer.color.label} ki baari — Dice roll karo"
                    },
                    color = Color(0xFFFFD700), fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                // Dice + Roll button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dice display — Ludo King style
                    Box(
                        Modifier.size(64.dp)
                            .shadow(12.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFEE58)))
                            )
                            .rotate(if (diceRolling) diceAngle else 0f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(diceEmoji(gameState.diceValue), fontSize = 38.sp)
                    }

                    // Roll button
                    val canRoll = !gameState.diceRolled &&
                        gameState.currentPlayer.type == PlayerType.HUMAN &&
                        !gameState.isGameOver

                    Box(
                        Modifier.height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (canRoll)
                                    Brush.horizontalGradient(listOf(Color(0xFFFF6F00), Color(0xFFFFD600)))
                                else
                                    Brush.horizontalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF555555)))
                            )
                            .shadow(if (canRoll) 8.dp else 0.dp, RoundedCornerShape(28.dp))
                            .clickable(enabled = canRoll) {
                                scope.launch {
                                    diceRolling = true
                                    delay(500)
                                    engine.rollDice()
                                    gameState = engine.state
                                    diceRolling = false
                                }
                            }
                            .padding(horizontal = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (diceRolling) "Rolling... 🎲" else "🎲 ROLL",
                            color = if (canRoll) Color(0xFF1A0A00) else Color.Gray,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Game result dialog
        if (showResult) {
            GameResultDialog(
                gameState = gameState,
                engine = engine,
                mode = mode,
                onPlayAgain = {
                    showResult = false
                    // Reset engine
                    val newEngine = LudoEngine(playerCount, playerTypes)
                    engine.state = newEngine.state
                    gameState = engine.state
                },
                onExit = onBack
            )
        }
    }
}

@Composable
private fun PlayerIndicatorRow(state: LudoGameState) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF2D1500))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        state.players.forEach { player ->
            val isCurrent = player.color == state.currentPlayer.color
            val finishedTokens = player.tokens.count { it.isFinished }
            val borderColor = if (isCurrent) Color(0xFFFFD700) else Color.Transparent

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) Color(player.color.hex).copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .then(
                        if (isCurrent) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Color circle with crown if winning
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        Modifier.size(22.dp).shadow(4.dp, CircleShape).clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(player.color.hex), Color(player.color.darkHex))
                                )
                            )
                    )
                    if (player.finishOrder == 1) {
                        Text("👑", fontSize = 10.sp, modifier = Modifier.offset(x = 4.dp, y = (-4).dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    player.name.take(5),
                    color = if (isCurrent) Color.White else Color(0xFF888888),
                    fontSize = 10.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                // Token progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(4) { i ->
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(
                                    if (i < finishedTokens) Color(player.color.hex)
                                    else Color(0xFF444444)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameResultDialog(
    gameState: LudoGameState,
    engine: LudoEngine,
    mode: LudoMode,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A1A2E))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🏆 Game Over!", color = Color(0xFFFFD700), fontSize = 22.sp, fontWeight = FontWeight.Bold)

            gameState.finishOrder.forEachIndexed { idx, color ->
                val rank = idx + 1
                val prize = engine.calculatePrize(gameState.players.size, rank)
                val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "4️⃣" }

                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(color.hex).copy(alpha = 0.2f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$medal ${color.label}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (mode != LudoMode.VS_COMPUTER && prize > 0) {
                        Text("+$prize 🪙", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF333333))
                        .clickable { onExit() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Exit", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                        .clickable { onPlayAgain() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Play Again", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun diceEmoji(value: Int) = when (value) {
    1 -> "⚀"; 2 -> "⚁"; 3 -> "⚂"; 4 -> "⚃"; 5 -> "⚄"; 6 -> "⚅"
    else -> "🎲"
}

enum class LudoMode { VS_COMPUTER, LOCAL, ONLINE }

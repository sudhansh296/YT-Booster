package com.ytsubexchange.ui.ludo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ── Standard Ludo Board Layout ────────────────────────────────
// 15x15 grid
// RED    = top-left  (0,0)
// GREEN  = top-right (9,0)
// YELLOW = bottom-right (9,9)
// BLUE   = bottom-left (0,9)

@Composable
fun LudoBoardView(
    state: LudoGameState,
    onTokenClick: (LudoColor, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val movableTokens = if (state.diceRolled) state.getMovableTokensForCurrent() else emptyList()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a"
    )

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val boardDp = minOf(maxWidth, maxHeight)
        val cellDp = boardDp / 15f

        // Draw board
        Canvas(Modifier.size(boardDp)) {
            val c = size.width / 15f
            drawBoard(c)
        }

        // Draw tokens as Composables (so they render on top correctly)
        state.players.forEach { player ->
            player.tokens.forEach { token ->
                if (!token.isFinished) {
                    val (col, row) = tokenGridPos(player.color, token)
                    val isMovable = movableTokens.any { it.color == player.color && it.id == token.id }

                    // Token position in Dp
                    val tokenSize = cellDp * 0.75f
                    val offsetX = cellDp * col + (cellDp - tokenSize) / 2f
                    val offsetY = cellDp * row + (cellDp - tokenSize) / 2f

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(tokenSize)
                            .shadow(if (isMovable) 8.dp else 3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    0f to Color.White.copy(0.85f),
                                    0.35f to Color(player.color.hex),
                                    1f to Color(player.color.darkHex)
                                )
                            )
                            .clickable(enabled = isMovable) { onTokenClick(player.color, token.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner circle
                        Box(
                            Modifier.size(tokenSize * 0.55f).clip(CircleShape)
                                .background(Color(player.color.darkHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${token.id + 1}",
                                color = Color.White,
                                fontSize = (tokenSize.value * 0.28f).sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        // Highlight
                        Box(
                            Modifier.size(tokenSize * 0.2f)
                                .offset(x = -(tokenSize * 0.18f), y = -(tokenSize * 0.18f))
                                .clip(CircleShape)
                                .background(Color.White.copy(0.55f))
                        )
                        // Movable glow
                        if (isMovable) {
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(Color(player.color.hex).copy(pulseAlpha * 0.4f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Token grid position (col, row) ───────────────────────────
fun tokenGridPos(color: LudoColor, token: LudoToken): Pair<Float, Float> {
    if (token.isAtBase) return baseGridPos(color, token.id)
    if (token.isFinished) return Pair(7f, 7f)
    return pathGridPos(color, token.position)
}

// Base positions — 4 tokens inside each colored home area
// Each home area is 6x6, tokens at positions within it
private fun baseGridPos(color: LudoColor, id: Int): Pair<Float, Float> {
    // 2x2 grid of tokens inside the home area
    val localPositions = listOf(
        Pair(1.3f, 1.3f), Pair(3.2f, 1.3f),
        Pair(1.3f, 3.2f), Pair(3.2f, 3.2f)
    )
    val (lc, lr) = localPositions[id]
    return when (color) {
        LudoColor.RED    -> Pair(lc, lr)           // top-left
        LudoColor.GREEN  -> Pair(9f + lc, lr)      // top-right
        LudoColor.YELLOW -> Pair(9f + lc, 9f + lr) // bottom-right
        LudoColor.BLUE   -> Pair(lc, 9f + lr)      // bottom-left
    }
}

// Standard Ludo path — 52 squares, starting from RED's entry point
// RED starts at col=6, row=13 (bottom of left column) going up
private val LUDO_PATH: List<Pair<Float, Float>> = listOf(
    // RED start area — left column going up (positions 0-5)
    Pair(6f, 13f), Pair(6f, 12f), Pair(6f, 11f), Pair(6f, 10f), Pair(6f, 9f),
    // Turn left — bottom row going left (6-11)
    Pair(5f, 8f), Pair(4f, 8f), Pair(3f, 8f), Pair(2f, 8f), Pair(1f, 8f), Pair(0f, 8f),
    // Turn up — left column (12)
    Pair(0f, 7f),
    // Turn right — top-left area (13-18)
    Pair(0f, 6f), Pair(1f, 6f), Pair(2f, 6f), Pair(3f, 6f), Pair(4f, 6f), Pair(5f, 6f),
    // Turn up — left column going up (19-23)
    Pair(6f, 5f), Pair(6f, 4f), Pair(6f, 3f), Pair(6f, 2f), Pair(6f, 1f), Pair(6f, 0f),
    // Turn right — top row (24)
    Pair(7f, 0f),
    // GREEN start area — right column going down (25-30)
    Pair(8f, 0f), Pair(8f, 1f), Pair(8f, 2f), Pair(8f, 3f), Pair(8f, 4f), Pair(8f, 5f),
    // Turn right — top-right area (31-36)
    Pair(9f, 6f), Pair(10f, 6f), Pair(11f, 6f), Pair(12f, 6f), Pair(13f, 6f), Pair(14f, 6f),
    // Turn down — right column (37)
    Pair(14f, 7f),
    // Turn left — right side going left (38-43)
    Pair(14f, 8f), Pair(13f, 8f), Pair(12f, 8f), Pair(11f, 8f), Pair(10f, 8f), Pair(9f, 8f),
    // YELLOW start area — right column going down (44-49)
    Pair(8f, 9f), Pair(8f, 10f), Pair(8f, 11f), Pair(8f, 12f), Pair(8f, 13f), Pair(8f, 14f),
    // Turn left — bottom row (50)
    Pair(7f, 14f)
    // position 51 = back to start (wraps to 0)
)

// Home column paths (6 squares leading to center)
private val HOME_COLS = mapOf(
    LudoColor.RED    to listOf(Pair(7f,13f),Pair(7f,12f),Pair(7f,11f),Pair(7f,10f),Pair(7f,9f),Pair(7f,8f)),
    LudoColor.GREEN  to listOf(Pair(1f,7f),Pair(2f,7f),Pair(3f,7f),Pair(4f,7f),Pair(5f,7f),Pair(6f,7f)),
    LudoColor.YELLOW to listOf(Pair(7f,1f),Pair(7f,2f),Pair(7f,3f),Pair(7f,4f),Pair(7f,5f),Pair(7f,6f)),
    LudoColor.BLUE   to listOf(Pair(13f,7f),Pair(12f,7f),Pair(11f,7f),Pair(10f,7f),Pair(9f,7f),Pair(8f,7f))
)

// Start offsets on the shared path for each color
private val START_OFFSET = mapOf(
    LudoColor.RED to 0, LudoColor.GREEN to 13,
    LudoColor.YELLOW to 26, LudoColor.BLUE to 39
)

private fun pathGridPos(color: LudoColor, position: Int): Pair<Float, Float> {
    if (position >= 52) {
        val col = HOME_COLS[color] ?: return Pair(7f, 7f)
        return col.getOrElse((position - 52).coerceIn(0, 5)) { Pair(7f, 7f) }
    }
    val offset = START_OFFSET[color] ?: 0
    return LUDO_PATH.getOrElse((offset + position) % 52) { Pair(7f, 7f) }
}

// ── Board Canvas Drawing ──────────────────────────────────────
private fun DrawScope.drawBoard(c: Float) {
    // Cream background
    drawRect(Color(0xFFF5E6C8))

    // ── 4 Colored Home Areas ──
    // RED = top-left
    drawHomeArea(0f, 0f, c * 6, Color(0xFFE53935), Color(0xFFB71C1C))
    // GREEN = top-right
    drawHomeArea(c * 9, 0f, c * 6, Color(0xFF43A047), Color(0xFF1B5E20))
    // YELLOW = bottom-right
    drawHomeArea(c * 9, c * 9, c * 6, Color(0xFFFDD835), Color(0xFFF9A825))
    // BLUE = bottom-left
    drawHomeArea(0f, c * 9, c * 6, Color(0xFF1E88E5), Color(0xFF0D47A1))

    // ── Path cells ──
    drawPathCells(c)

    // ── Home column lanes ──
    drawHomeLanes(c)

    // ── Center area ──
    drawCenter(c)

    // ── Grid lines ──
    for (i in 0..15) {
        drawLine(Color(0xFF8B6914).copy(0.3f), Offset(i * c, 0f), Offset(i * c, 15f * c), 0.8f)
        drawLine(Color(0xFF8B6914).copy(0.3f), Offset(0f, i * c), Offset(15f * c, i * c), 0.8f)
    }
    // Thick outer border
    drawRect(Color(0xFF8B6914), style = Stroke(c * 0.12f))

    // ── Safe star squares ──
    val safeIdx = setOf(8, 21, 34, 47) // non-start safe squares
    safeIdx.forEach { idx ->
        val (col, row) = LUDO_PATH[idx]
        drawStar(Offset(col * c + c / 2, row * c + c / 2), c * 0.3f, Color(0xFFFFD700))
    }
}

private fun DrawScope.drawHomeArea(x: Float, y: Float, sz: Float, main: Color, dark: Color) {
    drawRect(main, Offset(x, y), Size(sz, sz))
    val p = sz * 0.08f
    drawRect(Color(0xFFFFFDE7), Offset(x + p, y + p), Size(sz - p * 2, sz - p * 2))
    drawRect(main, Offset(x + p, y + p), Size(sz - p * 2, sz - p * 2), style = Stroke(sz * 0.04f))

    // 4 token slot circles
    val r = sz * 0.18f
    listOf(
        Offset(x + sz * 0.27f, y + sz * 0.27f),
        Offset(x + sz * 0.73f, y + sz * 0.27f),
        Offset(x + sz * 0.27f, y + sz * 0.73f),
        Offset(x + sz * 0.73f, y + sz * 0.73f)
    ).forEach { pos ->
        drawCircle(Color.Black.copy(0.15f), r + 2f, pos.copy(y = pos.y + 2f))
        drawCircle(dark, r, pos)
        drawCircle(main, r * 0.72f, pos)
        drawCircle(Color.White.copy(0.4f), r * 0.3f, pos.copy(x = pos.x - r * 0.2f, y = pos.y - r * 0.2f))
    }
}

private fun DrawScope.drawPathCells(c: Float) {
    LUDO_PATH.forEachIndexed { idx, (col, row) ->
        val bg = when (idx) {
            0  -> Color(0xFFE53935).copy(0.8f)  // RED start
            13 -> Color(0xFF43A047).copy(0.8f)  // GREEN start
            26 -> Color(0xFFFDD835).copy(0.8f)  // YELLOW start
            39 -> Color(0xFF1E88E5).copy(0.8f)  // BLUE start
            else -> Color(0xFFFFFDE7)
        }
        drawRect(bg, Offset(col * c, row * c), Size(c, c))
    }
}

private fun DrawScope.drawHomeLanes(c: Float) {
    val laneColors = mapOf(
        LudoColor.RED    to Color(0xFFE53935).copy(0.65f),
        LudoColor.GREEN  to Color(0xFF43A047).copy(0.65f),
        LudoColor.YELLOW to Color(0xFFFDD835).copy(0.65f),
        LudoColor.BLUE   to Color(0xFF1E88E5).copy(0.65f)
    )
    HOME_COLS.entries.forEach { entry ->
        val clr = laneColors[entry.key] ?: Color.Gray
        entry.value.forEach { (col, row) ->
            drawRect(clr, Offset(col * c, row * c), Size(c, c))
        }
    }
}

private fun DrawScope.drawCenter(c: Float) {
    val cx = 7.5f * c; val cy = 7.5f * c; val s = 1.5f * c
    drawRect(Color.White, Offset(6f * c, 6f * c), Size(3f * c, 3f * c))

    data class Tri(val color: Color, val pts: List<Offset>)
    listOf(
        Tri(Color(0xFFE53935), listOf(Offset(cx-s,cy+s),Offset(cx+s,cy+s),Offset(cx,cy))),
        Tri(Color(0xFF43A047), listOf(Offset(cx-s,cy-s),Offset(cx-s,cy+s),Offset(cx,cy))),
        Tri(Color(0xFFFDD835), listOf(Offset(cx-s,cy-s),Offset(cx+s,cy-s),Offset(cx,cy))),
        Tri(Color(0xFF1E88E5), listOf(Offset(cx+s,cy-s),Offset(cx+s,cy+s),Offset(cx,cy)))
    ).forEach { tri ->
        val path = Path().apply {
            moveTo(tri.pts[0].x,tri.pts[0].y); lineTo(tri.pts[1].x,tri.pts[1].y)
            lineTo(tri.pts[2].x,tri.pts[2].y); close()
        }
        drawPath(path, tri.color)
    }
    drawStar(Offset(cx, cy), c * 0.5f, Color(0xFFFFD700))
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val path = Path()
    val inner = r * 0.42f
    for (i in 0..9) {
        val a = (i * 36 - 90) * Math.PI / 180
        val rad = if (i % 2 == 0) r else inner
        val x = center.x + rad * cos(a).toFloat()
        val y = center.y + rad * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

// ── LudoBoard object for engine ───────────────────────────────
fun LudoGameState.getMovableTokensForCurrent(): List<LudoToken> {
    if (!diceRolled) return emptyList()
    val engine = LudoEngine(players.size)
    return engine.getMovableTokens(currentPlayer, diceValue)
}

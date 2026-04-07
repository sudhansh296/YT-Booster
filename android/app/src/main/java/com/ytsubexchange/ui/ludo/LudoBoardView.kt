package com.ytsubexchange.ui.ludo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ── Ludo King Style Board ─────────────────────────────────────

@Composable
fun LudoBoardView(
    state: LudoGameState,
    onTokenClick: (LudoColor, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val movableTokens = if (state.diceRolled) state.getMovableTokensForCurrent() else emptyList()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "scale"
    )

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val boardSize = minOf(maxWidth, maxHeight)
        val cell = boardSize / 15f

        // Board canvas
        Canvas(Modifier.size(boardSize)) {
            drawLudoKingBoard(cell.toPx())
        }

        // Tokens overlay
        state.players.forEach { player ->
            player.tokens.forEach { token ->
                if (!token.isFinished) {
                    val (col, row) = getTokenScreenPos(player.color, token)
                    val isMovable = movableTokens.any { it.color == player.color && it.id == token.id }
                    LudoKingToken(
                        color = player.color, tokenId = token.id,
                        isMovable = isMovable,
                        pulseAlpha = if (isMovable) pulseAlpha else 1f,
                        pulseScale = if (isMovable) pulseScale else 1f,
                        cell = cell, col = col, row = row,
                        onClick = { onTokenClick(player.color, token.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LudoKingToken(
    color: LudoColor, tokenId: Int, isMovable: Boolean,
    pulseAlpha: Float, pulseScale: Float,
    cell: Dp, col: Float, row: Float, onClick: () -> Unit
) {
    val size = cell * 0.72f * pulseScale
    val pad = (cell - size) / 2f

    Box(
        Modifier
            .offset(x = cell * col + pad, y = cell * row + pad)
            .size(size)
            .shadow(if (isMovable) 10.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.9f),
                    0.3f to Color(color.hex),
                    1f to Color(color.darkHex),
                    radius = size.value * 3
                )
            )
            .clickable(enabled = isMovable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Inner circle (Ludo King style)
        Box(
            Modifier.size(size * 0.55f).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(color.darkHex), Color(color.hex).copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${tokenId + 1}",
                color = Color.White,
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        // Highlight dot (3D effect)
        Box(
            Modifier.size(size * 0.22f).offset(x = -(size * 0.15f), y = -(size * 0.15f))
                .clip(CircleShape).background(Color.White.copy(alpha = 0.6f))
        )
        // Glow ring when movable
        if (isMovable) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(Color(color.hex).copy(alpha = pulseAlpha * 0.3f))
            )
        }
    }
}

// ── Board Drawing ─────────────────────────────────────────────
private fun DrawScope.drawLudoKingBoard(c: Float) {
    // Board background — cream/white like Ludo King
    drawRect(Color(0xFFF5E6C8))

    // Outer border
    drawRect(Color(0xFF8B6914), style = Stroke(c * 0.15f))

    // ── 4 Colored Home Areas (corners) ──
    drawLudoHomeArea(0f, 0f, c * 6, LudoColor.RED)
    drawLudoHomeArea(c * 9, 0f, c * 6, LudoColor.GREEN)
    drawLudoHomeArea(c * 9, c * 9, c * 6, LudoColor.YELLOW)
    drawLudoHomeArea(0f, c * 9, c * 6, LudoColor.BLUE)

    // ── Path cells ──
    drawAllPathCells(c)

    // ── Home columns (colored lanes) ──
    drawHomeColumnCells(c)

    // ── Center winning area ──
    drawCenterArea(c)

    // ── Grid lines ──
    drawBoardGrid(c)

    // ── Safe star markers ──
    drawStarMarkers(c)

    // ── Arrow markers on start squares ──
    drawStartArrows(c)
}

private fun DrawScope.drawLudoHomeArea(x: Float, y: Float, sz: Float, color: LudoColor) {
    val mainColor = Color(color.hex)
    val darkColor = Color(color.darkHex)

    // Outer colored border
    drawRect(mainColor, Offset(x, y), Size(sz, sz))

    // Inner white area
    val pad = sz * 0.08f
    drawRect(Color(0xFFFFFDE7), Offset(x + pad, y + pad), Size(sz - pad * 2, sz - pad * 2))

    // Inner colored border
    drawRect(mainColor, Offset(x + pad, y + pad), Size(sz - pad * 2, sz - pad * 2), style = Stroke(sz * 0.04f))

    // 4 token circles (Ludo King style — large circles with inner ring)
    val positions = listOf(
        Offset(x + sz * 0.27f, y + sz * 0.27f),
        Offset(x + sz * 0.73f, y + sz * 0.27f),
        Offset(x + sz * 0.27f, y + sz * 0.73f),
        Offset(x + sz * 0.73f, y + sz * 0.73f)
    )
    val r = sz * 0.19f
    positions.forEach { pos ->
        // Shadow
        drawCircle(Color.Black.copy(alpha = 0.2f), r + sz * 0.02f, pos.copy(y = pos.y + sz * 0.01f))
        // Outer ring
        drawCircle(darkColor, r, pos)
        // Inner fill
        drawCircle(mainColor, r * 0.75f, pos)
        // Highlight
        drawCircle(Color.White.copy(alpha = 0.4f), r * 0.35f, pos.copy(x = pos.x - r * 0.2f, y = pos.y - r * 0.2f))
    }
}

private fun DrawScope.drawAllPathCells(c: Float) {
    ludoPath.forEachIndexed { idx, (col, row) ->
        val x = col * c; val y = row * c
        val bg = when (idx) {
            0 -> Color(LudoColor.RED.hex).copy(alpha = 0.85f)
            13 -> Color(LudoColor.GREEN.hex).copy(alpha = 0.85f)
            26 -> Color(LudoColor.YELLOW.hex).copy(alpha = 0.85f)
            39 -> Color(LudoColor.BLUE.hex).copy(alpha = 0.85f)
            in LudoBoard.safeSquares -> Color(0xFFFFF9C4)
            else -> Color(0xFFFFFDE7)
        }
        drawRect(bg, Offset(x, y), Size(c, c))
    }
}

private fun DrawScope.drawHomeColumnCells(c: Float) {
    val colColors = mapOf(
        LudoColor.RED to Color(LudoColor.RED.hex).copy(alpha = 0.7f),
        LudoColor.GREEN to Color(LudoColor.GREEN.hex).copy(alpha = 0.7f),
        LudoColor.YELLOW to Color(LudoColor.YELLOW.hex).copy(alpha = 0.7f),
        LudoColor.BLUE to Color(LudoColor.BLUE.hex).copy(alpha = 0.7f)
    )
    homeColumns.entries.forEach { entry ->
        val clr = colColors[entry.key] ?: Color.Gray
        entry.value.forEach { (col, row) ->
            drawRect(clr, Offset(col * c, row * c), Size(c, c))
        }
    }
}

private fun DrawScope.drawCenterArea(c: Float) {
    val cx = 7.5f * c; val cy = 7.5f * c; val s = 1.5f * c

    // White center square
    drawRect(Color.White, Offset(6f * c, 6f * c), Size(3f * c, 3f * c))

    // 4 colored triangles
    data class Tri(val color: LudoColor, val pts: List<Offset>)
    val tris = listOf(
        Tri(LudoColor.RED, listOf(Offset(cx - s, cy + s), Offset(cx + s, cy + s), Offset(cx, cy))),
        Tri(LudoColor.GREEN, listOf(Offset(cx - s, cy - s), Offset(cx - s, cy + s), Offset(cx, cy))),
        Tri(LudoColor.YELLOW, listOf(Offset(cx - s, cy - s), Offset(cx + s, cy - s), Offset(cx, cy))),
        Tri(LudoColor.BLUE, listOf(Offset(cx + s, cy - s), Offset(cx + s, cy + s), Offset(cx, cy)))
    )
    tris.forEach { tri ->
        val path = Path().apply {
            moveTo(tri.pts[0].x, tri.pts[0].y)
            lineTo(tri.pts[1].x, tri.pts[1].y)
            lineTo(tri.pts[2].x, tri.pts[2].y)
            close()
        }
        drawPath(path, Color(tri.color.hex))
        drawPath(path, Color.White.copy(alpha = 0.15f))
    }

    // Center star
    drawStar(Offset(cx, cy), c * 0.55f, Color(0xFFFFD700))
    drawStar(Offset(cx, cy), c * 0.4f, Color(0xFFFF8F00))
}

private fun DrawScope.drawBoardGrid(c: Float) {
    val stroke = Color(0xFF8B6914).copy(alpha = 0.5f)
    val thick = c * 0.04f

    // Outer path border
    for (i in 6..9) {
        // Vertical lines in path area
        drawLine(stroke, Offset(i * c, 0f), Offset(i * c, 6f * c), thick)
        drawLine(stroke, Offset(i * c, 9f * c), Offset(i * c, 15f * c), thick)
    }
    for (i in 6..9) {
        // Horizontal lines
        drawLine(stroke, Offset(0f, i * c), Offset(6f * c, i * c), thick)
        drawLine(stroke, Offset(9f * c, i * c), Offset(15f * c, i * c), thick)
    }
    // Full grid thin lines
    val thin = Color(0xFF8B6914).copy(alpha = 0.2f)
    for (i in 0..15) {
        drawLine(thin, Offset(i * c, 0f), Offset(i * c, 15f * c), 0.5f)
        drawLine(thin, Offset(0f, i * c), Offset(15f * c, i * c), 0.5f)
    }
}

private fun DrawScope.drawStarMarkers(c: Float) {
    LudoBoard.safeSquares.forEach { idx ->
        if (idx < ludoPath.size && idx != 0 && idx != 13 && idx != 26 && idx != 39) {
            val (col, row) = ludoPath[idx]
            drawStar(Offset(col * c + c / 2, row * c + c / 2), c * 0.32f, Color(0xFFFFD700))
        }
    }
}

private fun DrawScope.drawStartArrows(c: Float) {
    // Draw colored arrows on start squares
    val starts = listOf(
        Triple(0, LudoColor.RED, 90f),   // pointing up
        Triple(13, LudoColor.GREEN, 180f),
        Triple(26, LudoColor.YELLOW, 270f),
        Triple(39, LudoColor.BLUE, 0f)
    )
    starts.forEach { (idx, color, angle) ->
        if (idx < ludoPath.size) {
            val (col, row) = ludoPath[idx]
            val cx = col * c + c / 2; val cy = row * c + c / 2
            drawArrow(Offset(cx, cy), c * 0.35f, angle, Color.White.copy(alpha = 0.9f))
        }
    }
}

private fun DrawScope.drawArrow(center: Offset, size: Float, angleDeg: Float, color: Color) {
    val rad = angleDeg * Math.PI / 180
    val path = Path().apply {
        val tip = Offset(center.x + size * cos(rad).toFloat(), center.y + size * sin(rad).toFloat())
        val left = Offset(
            center.x + size * 0.5f * cos(rad + 2.4f).toFloat(),
            center.y + size * 0.5f * sin(rad + 2.4f).toFloat()
        )
        val right = Offset(
            center.x + size * 0.5f * cos(rad - 2.4f).toFloat(),
            center.y + size * 0.5f * sin(rad - 2.4f).toFloat()
        )
        moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val inner = radius * 0.45f
    for (i in 0..9) {
        val angle = (i * 36 - 90) * Math.PI / 180
        val r = if (i % 2 == 0) radius else inner
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(radius * 0.1f))
}

// ── Token & Path Positions ────────────────────────────────────
private fun getTokenScreenPos(color: LudoColor, token: LudoToken): Pair<Float, Float> {
    if (token.isAtBase) return getBasePos(color, token.id)
    if (token.isFinished) return Pair(7f, 7f)
    return getPathPos(color, token.position)
}

private fun getBasePos(color: LudoColor, tokenId: Int): Pair<Float, Float> {
    val offsets = listOf(Pair(1.2f, 1.2f), Pair(2.9f, 1.2f), Pair(1.2f, 2.9f), Pair(2.9f, 2.9f))
    val (dc, dr) = offsets[tokenId]
    return when (color) {
        LudoColor.RED -> Pair(dc, dr)
        LudoColor.GREEN -> Pair(9f + dc, dr)
        LudoColor.YELLOW -> Pair(9f + dc, 9f + dr)
        LudoColor.BLUE -> Pair(dc, 9f + dr)
    }
}

private val ludoPath: List<Pair<Float, Float>> = listOf(
    Pair(6f, 13f), Pair(6f, 12f), Pair(6f, 11f), Pair(6f, 10f), Pair(6f, 9f),
    Pair(5f, 8f), Pair(4f, 8f), Pair(3f, 8f), Pair(2f, 8f), Pair(1f, 8f), Pair(0f, 8f),
    Pair(0f, 7f),
    Pair(0f, 6f), Pair(1f, 6f), Pair(2f, 6f), Pair(3f, 6f), Pair(4f, 6f), Pair(5f, 6f),
    Pair(6f, 5f), Pair(6f, 4f), Pair(6f, 3f), Pair(6f, 2f), Pair(6f, 1f), Pair(6f, 0f),
    Pair(7f, 0f),
    Pair(8f, 0f), Pair(8f, 1f), Pair(8f, 2f), Pair(8f, 3f), Pair(8f, 4f), Pair(8f, 5f),
    Pair(9f, 6f), Pair(10f, 6f), Pair(11f, 6f), Pair(12f, 6f), Pair(13f, 6f), Pair(14f, 6f),
    Pair(14f, 7f),
    Pair(14f, 8f), Pair(13f, 8f), Pair(12f, 8f), Pair(11f, 8f), Pair(10f, 8f), Pair(9f, 8f),
    Pair(8f, 9f), Pair(8f, 10f), Pair(8f, 11f), Pair(8f, 12f), Pair(8f, 13f), Pair(8f, 14f),
    Pair(7f, 14f)
)

private val homeColumns = mapOf(
    LudoColor.RED to listOf(Pair(7f, 13f), Pair(7f, 12f), Pair(7f, 11f), Pair(7f, 10f), Pair(7f, 9f), Pair(7f, 8f)),
    LudoColor.GREEN to listOf(Pair(1f, 7f), Pair(2f, 7f), Pair(3f, 7f), Pair(4f, 7f), Pair(5f, 7f), Pair(6f, 7f)),
    LudoColor.YELLOW to listOf(Pair(7f, 1f), Pair(7f, 2f), Pair(7f, 3f), Pair(7f, 4f), Pair(7f, 5f), Pair(7f, 6f)),
    LudoColor.BLUE to listOf(Pair(13f, 7f), Pair(12f, 7f), Pair(11f, 7f), Pair(10f, 7f), Pair(9f, 7f), Pair(8f, 7f))
)

private fun getPathPos(color: LudoColor, position: Int): Pair<Float, Float> {
    if (position >= 52) {
        val col = homeColumns[color] ?: return Pair(7f, 7f)
        return col.getOrElse((position - 52).coerceIn(0, col.size - 1)) { Pair(7f, 7f) }
    }
    val offset = LudoBoard.startOffset[color] ?: 0
    return ludoPath.getOrElse((offset + position) % 52) { Pair(7f, 7f) }
}

fun LudoGameState.getMovableTokensForCurrent(): List<LudoToken> {
    if (!diceRolled) return emptyList()
    val engine = LudoEngine(players.size)
    return engine.getMovableTokens(currentPlayer, diceValue)
}

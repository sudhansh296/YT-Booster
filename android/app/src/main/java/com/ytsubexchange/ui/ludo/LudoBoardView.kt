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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ── 2.5D Ludo Board ──────────────────────────────────────────
// Board is 15x15 cells. Each cell = boardSize/15

@Composable
fun LudoBoardView(
    state: LudoGameState,
    onTokenClick: (LudoColor, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val movableTokens = if (state.diceRolled) {
        state.getMovableTokensForCurrent()
    } else emptyList()

    // Pulse animation for movable tokens
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val boardSize = minOf(maxWidth, maxHeight)
        val cellSize = boardSize / 15f

        Canvas(modifier = Modifier.size(boardSize)) {
            drawLudoBoard(cellSize.toPx())
        }

        // Draw tokens on top
        state.players.forEach { player ->
            player.tokens.forEach { token ->
                if (!token.isFinished) {
                    val (col, row) = getTokenScreenPos(player.color, token)
                    val isMovable = movableTokens.any { it.color == player.color && it.id == token.id }

                    TokenView(
                        color = player.color,
                        tokenId = token.id,
                        isMovable = isMovable,
                        pulseAlpha = if (isMovable) pulseAlpha else 1f,
                        cellSize = cellSize,
                        col = col,
                        row = row,
                        onClick = { onTokenClick(player.color, token.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenView(
    color: LudoColor,
    tokenId: Int,
    isMovable: Boolean,
    pulseAlpha: Float,
    cellSize: Dp,
    col: Float,
    row: Float,
    onClick: () -> Unit
) {
    val tokenSize = cellSize * 0.7f
    val offset = cellSize * 0.15f

    Box(
        modifier = Modifier
            .offset(x = cellSize * col + offset, y = cellSize * row + offset)
            .size(tokenSize)
            .shadow(if (isMovable) 8.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(color.hex).copy(alpha = pulseAlpha),
                        Color(color.darkHex).copy(alpha = pulseAlpha)
                    )
                )
            )
            .clickable(enabled = isMovable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 3D highlight
        Box(
            Modifier
                .size(tokenSize * 0.4f)
                .offset(x = -(tokenSize * 0.1f), y = -(tokenSize * 0.1f))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.4f))
        )
        Text(
            text = "${tokenId + 1}",
            color = Color.White,
            fontSize = (tokenSize.value * 0.35f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Get screen grid position (col, row) for a token
private fun getTokenScreenPos(color: LudoColor, token: LudoToken): Pair<Float, Float> {
    if (token.isAtBase) {
        return getBasePos(color, token.id)
    }
    if (token.isFinished) {
        return getHomeCenter(color)
    }
    return getPathPos(color, token.position)
}

// Base positions (4 tokens in each colored home area)
private fun getBasePos(color: LudoColor, tokenId: Int): Pair<Float, Float> {
    val baseOffsets = listOf(
        Pair(1.3f, 1.3f), Pair(2.8f, 1.3f),
        Pair(1.3f, 2.8f), Pair(2.8f, 2.8f)
    )
    val (dc, dr) = baseOffsets[tokenId]
    return when (color) {
        LudoColor.RED -> Pair(dc, dr)
        LudoColor.GREEN -> Pair(9f + dc, dr)
        LudoColor.YELLOW -> Pair(9f + dc, 9f + dr)
        LudoColor.BLUE -> Pair(dc, 9f + dr)
    }
}

private fun getHomeCenter(color: LudoColor): Pair<Float, Float> {
    return when (color) {
        LudoColor.RED -> Pair(7f, 7f)
        LudoColor.GREEN -> Pair(7f, 7f)
        LudoColor.YELLOW -> Pair(7f, 7f)
        LudoColor.BLUE -> Pair(7f, 7f)
    }
}

// Standard Ludo path coordinates (15x15 grid)
private val ludoPath: List<Pair<Float, Float>> = listOf(
    // RED start (bottom-left area, going right)
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

// Home column paths for each color
private val homeColumns = mapOf(
    LudoColor.RED to listOf(Pair(7f, 13f), Pair(7f, 12f), Pair(7f, 11f), Pair(7f, 10f), Pair(7f, 9f), Pair(7f, 8f)),
    LudoColor.GREEN to listOf(Pair(1f, 7f), Pair(2f, 7f), Pair(3f, 7f), Pair(4f, 7f), Pair(5f, 7f), Pair(6f, 7f)),
    LudoColor.YELLOW to listOf(Pair(7f, 1f), Pair(7f, 2f), Pair(7f, 3f), Pair(7f, 4f), Pair(7f, 5f), Pair(7f, 6f)),
    LudoColor.BLUE to listOf(Pair(13f, 7f), Pair(12f, 7f), Pair(11f, 7f), Pair(10f, 7f), Pair(9f, 7f), Pair(8f, 7f))
)

private fun getPathPos(color: LudoColor, position: Int): Pair<Float, Float> {
    if (position >= 52) {
        val homeCol = homeColumns[color] ?: return Pair(7f, 7f)
        val idx = (position - 52).coerceIn(0, homeCol.size - 1)
        return homeCol[idx]
    }
    val offset = LudoBoard.startOffset[color] ?: 0
    val pathIdx = (offset + position) % 52
    return ludoPath.getOrElse(pathIdx) { Pair(7f, 7f) }
}

// ── Board Drawing ─────────────────────────────────────────────
private fun DrawScope.drawLudoBoard(cellPx: Float) {
    val w = size.width
    val h = size.height

    // Background
    drawRect(Color(0xFF1A1A2E))

    // Draw colored home areas (corners)
    drawHomeArea(0f, 0f, cellPx * 6, LudoColor.RED)
    drawHomeArea(cellPx * 9, 0f, cellPx * 6, LudoColor.GREEN)
    drawHomeArea(cellPx * 9, cellPx * 9, cellPx * 6, LudoColor.YELLOW)
    drawHomeArea(0f, cellPx * 9, cellPx * 6, LudoColor.BLUE)

    // Draw path cells
    drawPathCells(cellPx)

    // Draw home columns
    drawHomeColumns(cellPx)

    // Center triangle (winning area)
    drawCenterTriangles(cellPx)

    // Grid lines
    drawGridLines(cellPx)

    // Safe star markers
    drawSafeMarkers(cellPx)
}

private fun DrawScope.drawHomeArea(x: Float, y: Float, size: Float, color: LudoColor) {
    // Outer border
    drawRect(
        color = Color(color.hex),
        topLeft = Offset(x, y),
        size = Size(size, size)
    )
    // Inner white area for tokens
    val padding = size * 0.1f
    drawRect(
        color = Color(0xFFF5F5F5),
        topLeft = Offset(x + padding, y + padding),
        size = Size(size - padding * 2, size - padding * 2)
    )
    // 4 token circles
    val tokenR = size * 0.18f
    val positions = listOf(
        Offset(x + size * 0.28f, y + size * 0.28f),
        Offset(x + size * 0.72f, y + size * 0.28f),
        Offset(x + size * 0.28f, y + size * 0.72f),
        Offset(x + size * 0.72f, y + size * 0.72f)
    )
    positions.forEach { pos ->
        drawCircle(Color(color.darkHex).copy(alpha = 0.3f), tokenR, pos)
        drawCircle(Color(color.hex).copy(alpha = 0.5f), tokenR * 0.7f, pos)
    }
}

private fun DrawScope.drawPathCells(cellPx: Float) {
    // Draw all path squares
    ludoPath.forEachIndexed { idx, (col, row) ->
        val x = col * cellPx
        val y = row * cellPx
        val isStart = idx == 0 || idx == 13 || idx == 26 || idx == 39
        val isSafe = idx in LudoBoard.safeSquares

        val bgColor = when {
            idx == 0 -> Color(LudoColor.RED.hex).copy(alpha = 0.6f)
            idx == 13 -> Color(LudoColor.GREEN.hex).copy(alpha = 0.6f)
            idx == 26 -> Color(LudoColor.YELLOW.hex).copy(alpha = 0.6f)
            idx == 39 -> Color(LudoColor.BLUE.hex).copy(alpha = 0.6f)
            isSafe -> Color(0xFFFFFFFF).copy(alpha = 0.15f)
            else -> Color(0xFFFFFFFF).copy(alpha = 0.08f)
        }

        drawRect(bgColor, Offset(x, y), Size(cellPx, cellPx))
        drawRect(Color.White.copy(alpha = 0.1f), Offset(x, y), Size(cellPx, cellPx), style = Stroke(1f))
    }
}

private fun DrawScope.drawHomeColumns(cellPx: Float) {
    val colors = mapOf(
        LudoColor.RED to Color(LudoColor.RED.hex).copy(alpha = 0.4f),
        LudoColor.GREEN to Color(LudoColor.GREEN.hex).copy(alpha = 0.4f),
        LudoColor.YELLOW to Color(LudoColor.YELLOW.hex).copy(alpha = 0.4f),
        LudoColor.BLUE to Color(LudoColor.BLUE.hex).copy(alpha = 0.4f)
    )
    homeColumns.forEach { (color, cells) ->
        val cellColor = colors[color] ?: Color.Gray
        cells.forEach { (col, row) ->
            drawRect(cellColor, Offset(col * cellPx, row * cellPx), Size(cellPx, cellPx))
        }
    }
}

private fun DrawScope.drawCenterTriangles(cellPx: Float) {
    val cx = 7.5f * cellPx
    val cy = 7.5f * cellPx
    val s = 1.5f * cellPx

    // 4 triangles pointing to center
    data class Triangle(val color: LudoColor, val pts: List<Offset>)
    val triangles = listOf(
        Triangle(LudoColor.RED, listOf(Offset(cx - s, cy + s), Offset(cx + s, cy + s), Offset(cx, cy))),
        Triangle(LudoColor.GREEN, listOf(Offset(cx - s, cy - s), Offset(cx - s, cy + s), Offset(cx, cy))),
        Triangle(LudoColor.YELLOW, listOf(Offset(cx - s, cy - s), Offset(cx + s, cy - s), Offset(cx, cy))),
        Triangle(LudoColor.BLUE, listOf(Offset(cx + s, cy - s), Offset(cx + s, cy + s), Offset(cx, cy)))
    )

    triangles.forEach { tri ->
        val path = Path().apply {
            moveTo(tri.pts[0].x, tri.pts[0].y)
            lineTo(tri.pts[1].x, tri.pts[1].y)
            lineTo(tri.pts[2].x, tri.pts[2].y)
            close()
        }
        drawPath(path, Color(tri.color.hex).copy(alpha = 0.8f))
    }

    // Center star
    drawCircle(Color.White.copy(alpha = 0.9f), cellPx * 0.6f, Offset(cx, cy))
    drawCircle(Color(0xFFFFD700), cellPx * 0.4f, Offset(cx, cy))
}

private fun DrawScope.drawGridLines(cellPx: Float) {
    val paint = Color.White.copy(alpha = 0.05f)
    for (i in 0..15) {
        drawLine(paint, Offset(i * cellPx, 0f), Offset(i * cellPx, size.height), 0.5f)
        drawLine(paint, Offset(0f, i * cellPx), Offset(size.width, i * cellPx), 0.5f)
    }
}

private fun DrawScope.drawSafeMarkers(cellPx: Float) {
    LudoBoard.safeSquares.forEach { idx ->
        if (idx < ludoPath.size) {
            val (col, row) = ludoPath[idx]
            val cx = col * cellPx + cellPx / 2
            val cy = row * cellPx + cellPx / 2
            // Draw star
            drawStar(Offset(cx, cy), cellPx * 0.3f, Color(0xFFFFD700).copy(alpha = 0.8f))
        }
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val outerR = radius
    val innerR = radius * 0.4f
    for (i in 0..9) {
        val angle = (i * 36 - 90) * Math.PI / 180
        val r = if (i % 2 == 0) outerR else innerR
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

// Extension to get movable tokens for current player
fun LudoGameState.getMovableTokensForCurrent(): List<LudoToken> {
    if (!diceRolled) return emptyList()
    val engine = LudoEngine(players.size)
    return engine.getMovableTokens(currentPlayer, diceValue)
}

package com.ytsubexchange.ui.ludo

// ── Ludo Board Constants ──────────────────────────────────────
// Standard Ludo: 15x15 grid, 4 colors: RED, GREEN, YELLOW, BLUE
// Each player has 4 tokens, path length = 56 squares + 6 home column = 57 total

enum class LudoColor(val hex: Long, val darkHex: Long, val label: String) {
    RED(0xFFE53935, 0xFFB71C1C, "Red"),
    GREEN(0xFF43A047, 0xFF1B5E20, "Green"),
    YELLOW(0xFFFDD835, 0xFFF9A825, "Yellow"),
    BLUE(0xFF1E88E5, 0xFF0D47A1, "Blue")
}

enum class PlayerType { HUMAN, COMPUTER }

data class LudoToken(
    val id: Int,           // 0-3 within a player
    val color: LudoColor,
    var position: Int = -1, // -1 = home base, 0-56 = path, 57 = finished
    var isFinished: Boolean = false
) {
    val isAtBase get() = position == -1
    val isOnBoard get() = position in 0..56
}

data class LudoPlayer(
    val color: LudoColor,
    val type: PlayerType = PlayerType.HUMAN,
    val name: String = color.label,
    val tokens: List<LudoToken> = List(4) { LudoToken(it, color) },
    var finishOrder: Int = -1  // 1st, 2nd, 3rd, 4th
)

// Standard Ludo path positions (0-based, 52 squares on outer track)
// Each color starts at a different offset on the shared path
object LudoBoard {
    // Starting square index on the shared path for each color
    val startOffset = mapOf(
        LudoColor.RED to 0,
        LudoColor.GREEN to 13,
        LudoColor.YELLOW to 26,
        LudoColor.BLUE to 39
    )

    // Home column entry square (last square before home column)
    val homeEntry = mapOf(
        LudoColor.RED to 51,
        LudoColor.GREEN to 12,
        LudoColor.YELLOW to 25,
        LudoColor.BLUE to 38
    )

    // Safe squares (star squares + home squares)
    val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    // Convert token's logical position to board path index
    // position 0 = just entered board, position 51 = last outer square
    // position 52-57 = home column (6 squares)
    fun pathIndex(color: LudoColor, position: Int): Int {
        if (position < 0) return -1
        if (position > 57) return 57
        val offset = startOffset[color] ?: 0
        return if (position <= 51) (offset + position) % 52
        else 52 + (position - 52) // home column 0-5
    }

    fun isSafe(color: LudoColor, position: Int): Boolean {
        val idx = pathIndex(color, position)
        return idx in safeSquares || position >= 52
    }
}

data class LudoGameState(
    val players: List<LudoPlayer>,
    val currentPlayerIdx: Int = 0,
    val diceValue: Int = 0,
    val diceRolled: Boolean = false,
    val gamePhase: GamePhase = GamePhase.WAITING,
    val winner: LudoColor? = null,
    val finishOrder: List<LudoColor> = emptyList(),
    val consecutiveSixes: Int = 0,
    val message: String = ""
) {
    val currentPlayer get() = players[currentPlayerIdx]
    val isGameOver get() = gamePhase == GamePhase.FINISHED
}

enum class GamePhase {
    WAITING,    // Waiting for players
    PLAYING,    // Game in progress
    FINISHED    // Game over
}

class LudoEngine(
    val playerCount: Int = 4,
    val playerTypes: List<PlayerType> = List(4) { PlayerType.HUMAN }
) {
    private val colors = when (playerCount) {
        2 -> listOf(LudoColor.RED, LudoColor.YELLOW)
        3 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW)
        else -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
    }

    var state = LudoGameState(
        players = colors.mapIndexed { i, color ->
            LudoPlayer(color = color, type = playerTypes.getOrElse(i) { PlayerType.HUMAN })
        },
        gamePhase = GamePhase.PLAYING
    )

    fun rollDice(): Int {
        if (state.diceRolled) return state.diceValue
        val value = (1..6).random()
        val newConsecutive = if (value == 6) state.consecutiveSixes + 1 else 0

        // 3 consecutive sixes = skip turn
        if (newConsecutive >= 3) {
            state = state.copy(
                diceValue = value,
                diceRolled = true,
                consecutiveSixes = 0,
                message = "3 sixes! Turn skip!"
            )
            nextTurn()
            return value
        }

        val movable = getMovableTokens(state.currentPlayer, value)
        val msg = when {
            value == 6 -> "Six! Token bahar nikalo ya move karo"
            movable.isEmpty() -> "Koi move nahi — next player"
            else -> "Token choose karo"
        }

        state = state.copy(
            diceValue = value,
            diceRolled = true,
            consecutiveSixes = newConsecutive,
            message = msg
        )

        if (movable.isEmpty()) {
            nextTurn()
        }
        return value
    }

    fun moveToken(tokenId: Int): Boolean {
        if (!state.diceRolled) return false
        val player = state.currentPlayer
        val token = player.tokens.find { it.id == tokenId } ?: return false
        val dice = state.diceValue

        // Can't move finished token
        if (token.isFinished) return false

        // Token at base — needs 6 to come out
        if (token.isAtBase) {
            if (dice != 6) return false
            token.position = 0
            state = state.copy(message = "${player.color.label} token bahar aaya!")
        } else {
            val newPos = token.position + dice
            if (newPos > 57) return false // Can't overshoot home

            // Check if token reaches home
            if (newPos == 57) {
                token.position = 57
                token.isFinished = true
                checkFinish(player)
                state = state.copy(message = "${player.color.label} token ghar pahuncha! 🏠")
            } else {
                // Check for capture
                val captured = checkCapture(player.color, newPos)
                token.position = newPos
                if (captured) {
                    state = state.copy(message = "Token capture! Extra turn!")
                }
            }
        }

        // Update state
        val updatedPlayers = state.players.map { p ->
            if (p.color == player.color) p.copy(tokens = p.tokens.map { t ->
                if (t.id == tokenId) token else t
            }) else p
        }

        val isGameOver = state.finishOrder.size >= playerCount - 1

        state = state.copy(
            players = updatedPlayers,
            diceRolled = false,
            gamePhase = if (isGameOver) GamePhase.FINISHED else GamePhase.PLAYING
        )

        // Extra turn on 6 or capture (unless game over)
        if (!isGameOver && (dice == 6 || state.message.contains("capture"))) {
            // Same player rolls again
        } else if (!isGameOver) {
            nextTurn()
        }

        return true
    }

    private fun checkCapture(attackerColor: LudoColor, position: Int): Boolean {
        if (position < 0 || position > 51) return false
        val pathIdx = LudoBoard.pathIndex(attackerColor, position)
        if (pathIdx in LudoBoard.safeSquares) return false

        var captured = false
        val updatedPlayers = state.players.map { player ->
            if (player.color == attackerColor) player
            else {
                val updatedTokens = player.tokens.map { token ->
                    if (!token.isAtBase && !token.isFinished) {
                        val tokenPathIdx = LudoBoard.pathIndex(player.color, token.position)
                        if (tokenPathIdx == pathIdx) {
                            captured = true
                            token.copy(position = -1) // Send back to base
                        } else token
                    } else token
                }
                player.copy(tokens = updatedTokens)
            }
        }
        if (captured) state = state.copy(players = updatedPlayers)
        return captured
    }

    private fun checkFinish(player: LudoPlayer) {
        val allFinished = player.tokens.all { it.isFinished }
        if (allFinished && !state.finishOrder.contains(player.color)) {
            val newOrder = state.finishOrder + player.color
            val rank = newOrder.size
            val updatedPlayers = state.players.map { p ->
                if (p.color == player.color) p.copy(finishOrder = rank) else p
            }
            state = state.copy(
                finishOrder = newOrder,
                players = updatedPlayers,
                winner = if (newOrder.size == 1) player.color else state.winner
            )
        }
    }

    fun getMovableTokens(player: LudoPlayer, dice: Int): List<LudoToken> {
        return player.tokens.filter { token ->
            when {
                token.isFinished -> false
                token.isAtBase -> dice == 6
                else -> (token.position + dice) <= 57
            }
        }
    }

    private fun nextTurn() {
        var next = (state.currentPlayerIdx + 1) % state.players.size
        // Skip finished players
        var attempts = 0
        while (state.players[next].finishOrder > 0 && attempts < state.players.size) {
            next = (next + 1) % state.players.size
            attempts++
        }
        state = state.copy(
            currentPlayerIdx = next,
            diceRolled = false,
            consecutiveSixes = 0
        )
    }

    // Computer AI — simple: prefer capture > move furthest token > bring out token
    fun computerMove(): Pair<Int, Int>? {
        val player = state.currentPlayer
        if (player.type != PlayerType.COMPUTER) return null

        val dice = rollDice()
        val movable = getMovableTokens(player, dice)
        if (movable.isEmpty()) return null

        // Priority: capture > home stretch > furthest token
        val best = movable.maxByOrNull { token ->
            val newPos = if (token.isAtBase) 0 else token.position + dice
            val pathIdx = LudoBoard.pathIndex(player.color, newPos)
            // Check if this move captures an opponent
            val captureScore = state.players
                .filter { it.color != player.color }
                .flatMap { it.tokens }
                .count { opp ->
                    !opp.isAtBase && !opp.isFinished &&
                    LudoBoard.pathIndex(opp.color, opp.position) == pathIdx &&
                    pathIdx !in LudoBoard.safeSquares
                } * 100
            captureScore + newPos
        }

        return best?.let { Pair(it.id, dice) }
    }

    fun calculatePrize(playerCount: Int, rank: Int): Int {
        val pool = playerCount * 50
        return when (playerCount) {
            2 -> if (rank == 1) 75 else 0
            4 -> when (rank) {
                1 -> 75; 2 -> 50; 3 -> 25; else -> 0
            }
            3 -> when (rank) {
                1 -> 75; 2 -> 50; else -> 0
            }
            else -> 0
        }
    }
}

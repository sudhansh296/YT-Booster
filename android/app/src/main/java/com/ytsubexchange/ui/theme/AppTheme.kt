package com.ytsubexchange.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

// Global theme state
val isDarkTheme = mutableStateOf(true)

val LocalAppTheme = compositionLocalOf { true }

object AppColors {
    // Dark theme
    val darkBg = androidx.compose.ui.graphics.Color(0xFF0F0F0F)
    val darkCard = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val darkCardAlt = androidx.compose.ui.graphics.Color(0xFF222222)

    // Light theme
    val lightBg = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
    val lightCard = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val lightCardAlt = androidx.compose.ui.graphics.Color(0xFFEEEEEE)

    // Common
    val red = androidx.compose.ui.graphics.Color(0xFFFF0000)
    val gold = androidx.compose.ui.graphics.Color(0xFFFFD700)
    val green = androidx.compose.ui.graphics.Color(0xFF4CAF50)
    val blue = androidx.compose.ui.graphics.Color(0xFF29B6F6)
    val orange = androidx.compose.ui.graphics.Color(0xFFFF6B00)

    fun bg(dark: Boolean) = if (dark) darkBg else lightBg
    fun card(dark: Boolean) = if (dark) darkCard else lightCard
    fun cardAlt(dark: Boolean) = if (dark) darkCardAlt else lightCardAlt
    fun text(dark: Boolean) = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF111111)
    fun textSecondary(dark: Boolean) = if (dark) androidx.compose.ui.graphics.Color.Gray else androidx.compose.ui.graphics.Color(0xFF666666)
}

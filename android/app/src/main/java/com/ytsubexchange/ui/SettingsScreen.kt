package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSec = AppColors.textSecondary(dark)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { "Bearer ${authPrefs.getString("token", "")}" }
    val scope = rememberCoroutineScope()

    // Review state
    var selectedRating by remember { mutableStateOf(0) }
    var reviewComment by remember { mutableStateOf("") }
    var reviewSubmitted by remember { mutableStateOf(false) }
    var reviewLoading by remember { mutableStateOf(false) }
    var reviewMsg by remember { mutableStateOf("") }

    // Load existing review
    LaunchedEffect(Unit) {
        try {
            val resp = RetrofitClient.api.getMyReview(token)
            resp.review?.let {
                selectedRating = it.rating
                reviewComment = it.comment
                reviewSubmitted = true
            }
        } catch (e: Exception) { }
    }

    Column(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
            }
            Text("Settings", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Theme toggle
            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (dark) "🌙 Dark Mode" else "☀️ Light Mode", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(if (dark) "Dark theme on hai" else "Light theme on hai", color = textSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = dark,
                        onCheckedChange = { isDarkTheme.value = it; prefs.edit().putBoolean("dark_theme", it).apply() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF0000), uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF888888))
                    )
                }
            }

            // ── Rate & Review ─────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⭐", fontSize = 20.sp)
                        Text("Rate & Review", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (reviewSubmitted) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF4CAF50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("Submitted ✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("App kaisi lagi? Apna feedback do — hum improve karte rahenge!", color = textSec, fontSize = 13.sp)

                    // Star rating
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { star ->
                            Icon(
                                Icons.Default.Star, null,
                                tint = if (star <= selectedRating) Color(0xFFFFD700) else Color(0xFF555555),
                                modifier = Modifier.size(36.dp).clickable { selectedRating = star }
                            )
                        }
                    }
                    if (selectedRating > 0) {
                        Text(
                            when (selectedRating) {
                                1 -> "😞 Bahut bura"
                                2 -> "😕 Theek nahi"
                                3 -> "😐 Average"
                                4 -> "😊 Acha hai"
                                else -> "🤩 Ekdum best!"
                            },
                            color = when (selectedRating) {
                                1, 2 -> Color(0xFFFF6B6B)
                                3 -> Color(0xFFFFAA00)
                                else -> Color(0xFF4CAF50)
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Comment field
                    OutlinedTextField(
                        value = reviewComment,
                        onValueChange = { if (it.length <= 500) reviewComment = it },
                        placeholder = { Text("Koi problem hai? Kya naya chahte ho? (optional)", color = textSec, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("${reviewComment.length}/500", color = textSec, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))

                    if (reviewMsg.isNotEmpty()) {
                        Text(reviewMsg, color = if (reviewMsg.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
                    }

                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selectedRating > 0) Color(0xFFE53935) else Color(0xFF333333))
                            .clickable(enabled = selectedRating > 0 && !reviewLoading) {
                                scope.launch {
                                    reviewLoading = true
                                    reviewMsg = ""
                                    try {
                                        RetrofitClient.api.submitReview(token, com.ytsubexchange.data.ReviewRequest(selectedRating, reviewComment))
                                        reviewSubmitted = true
                                        reviewMsg = "✓ Review submit ho gaya! Shukriya 🙏"
                                    } catch (e: Exception) {
                                        reviewMsg = "❌ Submit nahi hua, dobara try karo"
                                    } finally {
                                        reviewLoading = false
                                    }
                                }
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reviewLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (reviewSubmitted) "Update Review" else "Submit Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // App info
            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App Info", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version", color = textSec, fontSize = 13.sp)
                        Text("1.0.0", color = textColor, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("App Name", color = textSec, fontSize = 13.sp)
                        Text("YT-Booster", color = textColor, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Support", color = textSec, fontSize = 13.sp)
                        Text("@GODCHEATOFFICIAL", color = Color(0xFF29B6F6), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

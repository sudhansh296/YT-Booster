package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.data.DailyTask
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Composable
fun DailyTasksScreen(onBack: () -> Unit) {
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSec = AppColors.textSecondary(dark)

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { "Bearer ${prefs.getString("token", "")}" }

    var tasks by remember { mutableStateOf<List<DailyTask>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var claimMsg by remember { mutableStateOf("") }
    var totalCoins by remember { mutableStateOf(0) }

    fun loadTasks() {
        scope.launch {
            loading = true
            try {
                val resp = RetrofitClient.api.getDailyTasks(token)
                tasks = resp.tasks
                totalCoins = tasks.filter { it.completed && !it.claimed }.sumOf { it.coinReward }
            } catch (e: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        // Complete login task
        try { RetrofitClient.api.completeLoginTask(token) } catch (e: Exception) { }
        loadTasks()
    }

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = textColor)
            }
            Text("🎯 Daily Tasks", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            if (totalCoins > 0) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFD700).copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Claim: +$totalCoins 🪙", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Info banner
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7).copy(0.2f), Color(0xFFE53935).copy(0.1f))))
            .padding(12.dp)) {
            Column {
                Text("Aaj ke tasks complete karo", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tasks midnight pe reset hote hain • Coins turant milte hain", color = textSec, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (claimMsg.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (claimMsg.contains("✅")) Color(0xFF4CAF50).copy(0.15f) else Color(0xFFFF6B6B).copy(0.15f))
                .padding(12.dp)) {
                Text(claimMsg, color = if (claimMsg.contains("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE53935))
            }
        } else if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Koi task nahi abhi", color = textSec, fontSize = 14.sp)
                    Text("Admin jald hi tasks add karega!", color = textSec, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks) { task ->
                    TaskCard(
                        task = task,
                        card = card,
                        textColor = textColor,
                        textSec = textSec,
                        onClaim = {
                            scope.launch {
                                try {
                                    val resp = RetrofitClient.api.claimTask(token, task._id)
                                    claimMsg = "✅ +${resp.earned} coins mile! Total: ${resp.coins} 🪙"
                                    loadTasks()
                                } catch (e: Exception) {
                                    claimMsg = "❌ ${e.message?.take(50) ?: "Error"}"
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DailyTask,
    card: Color,
    textColor: Color,
    textSec: Color,
    onClaim: () -> Unit
) {
    val progress = if (task.targetCount > 0) task.progress.toFloat() / task.targetCount else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Icon
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            task.claimed -> Color(0xFF4CAF50).copy(0.15f)
                            task.completed -> Color(0xFFFFD700).copy(0.15f)
                            else -> Color(0xFF7B2FF7).copy(0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (task.claimed) "✅" else task.icon, fontSize = 22.sp)
            }

            Column(Modifier.weight(1f)) {
                Text(task.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (task.description.isNotEmpty()) {
                    Text(task.description, color = textSec, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                // Progress bar
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF333333))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (task.completed) listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                                else listOf(Color(0xFF7B2FF7), Color(0xFFE53935))
                            )
                        ))
                }
                Spacer(Modifier.height(3.dp))
                Text("${task.progress}/${task.targetCount} • +${task.coinReward} 🪙", color = textSec, fontSize = 10.sp)
            }

            // Claim button
            if (task.completed && !task.claimed) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8F00))))
                        .clickable { onClaim() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Claim!", color = Color(0xFF1A0A00), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            } else if (task.claimed) {
                Text("Done ✓", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.ytsubexchange.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.test.data.TransactionData
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.ui.theme.isDarkTheme
import com.ytsubexchange.test.viewmodel.MainViewModel

@Composable
fun TransactionScreen(viewModel: MainViewModel, onBack: () -> Unit = {}) {
    val transactions by viewModel.transactions.collectAsState()
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadTransactions()
        kotlinx.coroutines.delay(500)
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().background(bg)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 22.sp, color = textColor)
            }
            Spacer(Modifier.width(4.dp))
            Text("Transaction History", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF0000))
            }
        } else if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Koi transaction nahi mila", color = textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { txn -> TransactionItem(txn, dark) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun TransactionItem(txn: TransactionData, dark: Boolean = true) {
    val isPositive = txn.coins > 0
    val coinColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
    val coinText = if (isPositive) "+${txn.coins}" else "${txn.coins}"

    val typeLabel = when (txn.type) {
        "earn" -> "Subscribe Earn"
        "earn_owner" -> "Owner Subscribe"
        "spend" -> "Subscribers Buy"
        "admin_add" -> "Bonus/Reward"
        "admin_remove" -> "Admin Deduct"
        else -> txn.type
    }

    val dateStr = if (txn.createdAt.length >= 10) txn.createdAt.substring(0, 10) else txn.createdAt

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    if (isPositive) (if (dark) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)) else (if (dark) Color(0xFF2A1A1A) else Color(0xFFFFEBEE)),
                    RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isPositive) "↑" else "↓", color = coinColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(typeLabel, color = AppColors.text(dark), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(txn.description, color = AppColors.textSecondary(dark), fontSize = 12.sp, maxLines = 1)
                Text(dateStr, color = AppColors.textSecondary(dark).copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Text(coinText, color = coinColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

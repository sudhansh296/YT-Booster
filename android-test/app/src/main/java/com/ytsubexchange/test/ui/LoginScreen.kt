package com.ytsubexchange.test.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.test.network.RetrofitClient

@Composable
fun LoginScreen() {
    val context = LocalContext.current

    val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
    val pendingRefToken = prefs.getString("pending_ref_token", null)

    val loginUrl = if (pendingRefToken != null) {
        "${RetrofitClient.BASE_URL}auth/youtube?test=1&ref_token=$pendingRefToken"
    } else {
        "${RetrofitClient.BASE_URL}auth/youtube?test=1"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 60.dp, bottom = 32.dp)
        ) {
            AsyncImage(
                model = com.ytsubexchange.test.R.drawable.subscribers_icon,
                contentDescription = "YT-Booster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Text("YT-Booster", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Subscribe & Earn Coins", fontSize = 16.sp, color = Color.Gray)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with YouTube", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }

            // Play Protect warning guide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1200))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚠️ Play Protect warning aaye toh?", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("1. \"More details\" tap karo", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text("2. \"Install anyway\" tap karo", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text("3. App bilkul safe hai ✅", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
            }

            // Google OAuth "Advanced" warning guide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A1A1A))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔐 Google login pe \"Advanced\" aaye toh?", color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("1. \"Advanced\" tap karo", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text("2. \"Go to YT Booster (unsafe)\" tap karo", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text("3. Data bilkul safe hai ✅", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
            }
        }
    }
}

# ── Aggressive Obfuscation ───────────────────────────────────
-repackageclasses 'x'
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames

# Keep UI theme globals
-keep class com.ytsubexchange.ui.theme.** { *; }
-keepclassmembers class com.ytsubexchange.ui.theme.** { *; }

# ── Remove Logging (no leaks in production) ──────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson - keep all type adapters and serializers
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Data models - MUST keep field names for JSON parsing
-keep class com.ytsubexchange.data.** { *; }
-keepclassmembers class com.ytsubexchange.data.** { *; }

# Network layer
-keep class com.ytsubexchange.network.** { *; }
-keepclassmembers class com.ytsubexchange.network.** { *; }

# ViewModel
-keep class com.ytsubexchange.viewmodel.** { *; }

# Socket.IO
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-keep class coil.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**

# Kotlin data classes - prevent field name obfuscation
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# WebRTC — release build mein strip mat karo
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**

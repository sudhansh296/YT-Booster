package com.ytsubexchange.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {
    private val encryptedUrl = byteArrayOf(
        0x32,0x2e,0x2e,0x2a,0x29,0x60,0x75,0x75,
        0x3b,0x2a,0x33,0x74,0x2a,0x33,0x39,0x28,
        0x23,0x2a,0x2e,0x35,0x74,0x33,0x34,0x75
    )
    private const val KEY = 0x5A

    val BASE_URL: String by lazy {
        String(encryptedUrl.map { (it.toInt() xor KEY).toByte() }.toByteArray())
    }

    // Trust all certs - self-signed SSL ke liye (testing only)
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.d("RetrofitClient", "Request: ${request.method} ${request.url}")
            val response = chain.proceed(request)
            android.util.Log.d("RetrofitClient", "Response: ${response.code} for ${request.url}")
            if (!response.isSuccessful) {
                android.util.Log.e("RetrofitClient", "Error response: ${response.code} ${response.message}")
            }
            response
        }
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

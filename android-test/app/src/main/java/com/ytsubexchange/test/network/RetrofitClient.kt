package com.ytsubexchange.test.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val encryptedUrl = byteArrayOf(
        0x32,0x2e,0x2e,0x2a,0x29,0x60,0x75,0x75,0x3b,0x2a,
        0x33,0x74,0x2a,0x33,0x39,0x28,0x23,0x2a,0x2e,0x35,
        0x74,0x33,0x34,0x75
    )
    private const val KEY = 0x5A

    val BASE_URL: String by lazy {
        String(encryptedUrl.map { (it.toInt() xor KEY).toByte() }.toByteArray())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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

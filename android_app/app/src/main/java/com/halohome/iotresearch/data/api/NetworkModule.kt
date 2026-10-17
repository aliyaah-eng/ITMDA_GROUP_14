package com.halohome.iotresearch.data.api

import android.content.Context
import android.content.SharedPreferences
import com.halohome.iotresearch.HaloHomeApplication
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton networking manager for the mobile application.
 * Handles Retrofit initialization, persistent preference storage for the server URL,
 * and automatic Bearer authentication header injection.
 */
object NetworkModule {

    private const val PREFS_NAME = "SafeHome_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PARTICIPANT_ID = "participant_id"
    private const val KEY_IS_COMMISSIONED = "is_commissioned"

    // Default loopback for Android Studio emulator. Can be changed via the in-app settings dialog.
    const val DEFAULT_BASE_URL = "http://10.0.2.2:5000/"

    private val prefs: SharedPreferences by lazy {
        HaloHomeApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Active API base URL. Stored in SharedPreferences so custom addresses
     * (e.g. LAN IP or production domain https://api.rickhomelab.co.za/) persist across app restarts.
     */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            val formatted = if (value.endsWith("/")) value else "$value/"
            prefs.edit().putString(KEY_BASE_URL, formatted).apply()
            // Reset cached Retrofit client so subsequent requests use the new base URL
            retrofitInstance = null
        }

    /** Bearer authentication token issued on register/login. */
    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        }

    /** Database user identifier. */
    var userId: Int
        get() = prefs.getInt(KEY_USER_ID, -1)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    /** Pseudonymized participant identifier (e.g., P-001). */
    var participantId: String
        get() = prefs.getString(KEY_PARTICIPANT_ID, "P-001") ?: "P-001"
        set(value) = prefs.edit().putString(KEY_PARTICIPANT_ID, value).apply()

    /** Whether the user has successfully commissioned their gateway. */
    var isCommissioned: Boolean
        get() = prefs.getBoolean(KEY_IS_COMMISSIONED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_COMMISSIONED, value).apply()

    /** Clears session credentials upon logout. */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_PARTICIPANT_ID)
            .remove(KEY_IS_COMMISSIONED)
            .apply()
    }

    /**
     * Interceptor that injects Bearer token and JSON headers into every outgoing HTTP request.
     */
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
            authToken?.let { token ->
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            builder.addHeader("Accept", "application/json")
            builder.addHeader("Content-Type", "application/json")
            return chain.proceed(builder.build())
        }
    }

    private var retrofitInstance: Retrofit? = null

    /**
     * Lazily provides the configured Retrofit API client with logging and timeouts.
     */
    val apiService: ApiService
        get() {
            if (retrofitInstance == null) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor())
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                retrofitInstance = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofitInstance!!.create(ApiService::class.java)
        }
}

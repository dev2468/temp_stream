package com.example.streamchat.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.streamchat.R
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Repository for managing user authentication and session
 * (Now using EncryptedSharedPreferences for secure token storage)
 */
class ChatRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val http = OkHttpClient()

    // ✅ Secure persistent storage for tokens & user info
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var tokenProvider: TokenProvider? = null

    // User session management
    fun saveUser(user: User, token: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_IMAGE, user.image)
            putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun getCurrentUser(): User? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return User(
            id = userId,
            name = prefs.getString(KEY_USER_NAME, "") ?: "",
            image = prefs.getString(KEY_USER_IMAGE, "") ?: ""
        )
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun setTokenProvider(provider: TokenProvider) {
        tokenProvider = provider
    }

    fun getTokenProvider(): TokenProvider? = tokenProvider

    fun clearSession() {
        prefs.edit().clear().apply()
        tokenProvider = null
    }

    fun isLoggedIn(): Boolean = getCurrentUser() != null

    // Fetch JWT from backend token server and upsert user there
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetchTokenFromServer(userId: String, name: String, image: String = ""): String =
        withContext(Dispatchers.IO) {
            val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
            val url = buildString {
                append(baseUrl).append("/token?user_id=")
                append(java.net.URLEncoder.encode(userId, Charsets.UTF_8))
                if (name.isNotBlank()) {
                    append("&name=").append(java.net.URLEncoder.encode(name, Charsets.UTF_8))
                }
                if (image.isNotBlank()) {
                    append("&image=").append(java.net.URLEncoder.encode(image, Charsets.UTF_8))
                }
            }
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Token server error: ${resp.code}")
                val body = resp.body?.string() ?: throw IllegalStateException("Empty response from token server")
                val token = JSONObject(body).optString("token")
                if (token.isNullOrBlank()) throw IllegalStateException("Invalid token response")
                token
            }
        }

    // Fetch Stream token using Supabase access token
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetchTokenWithSupabaseAuth(
        supabaseAccessToken: String,
        name: String = "",
        image: String = ""
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = buildString {
            append(baseUrl).append("/token")
            val params = mutableListOf<String>()
            if (name.isNotBlank()) params.add("name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}")
            if (image.isNotBlank()) params.add("image=${java.net.URLEncoder.encode(image, Charsets.UTF_8)}")
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $supabaseAccessToken")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                throw IllegalStateException("Token server error ${resp.code}: $errorBody")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response from token server")
            val token = JSONObject(body).optString("token")
            if (token.isNullOrBlank()) throw IllegalStateException("Invalid token response")
            token
        }
    }

    // Fetch Stream token using Firebase ID token
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetchTokenWithFirebaseAuth(
        firebaseIdToken: String,
        userId: String,
        name: String = "",
        image: String = ""
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = buildString {
            append(baseUrl).append("/token")
            val params = mutableListOf<String>()
            params.add("user_id=${java.net.URLEncoder.encode(userId, Charsets.UTF_8)}")
            if (name.isNotBlank()) params.add("name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}")
            if (image.isNotBlank()) params.add("image=${java.net.URLEncoder.encode(image, Charsets.UTF_8)}")
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                throw IllegalStateException("Token server error ${resp.code}: $errorBody")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response from token server")
            val token = JSONObject(body).optString("token")
            if (token.isNullOrBlank()) throw IllegalStateException("Invalid token response")
            token
        }
    }

    companion object {
        private const val PREFS_NAME = "stream_chat_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_IMAGE = "user_image"
        private const val KEY_TOKEN = "user_token"

        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

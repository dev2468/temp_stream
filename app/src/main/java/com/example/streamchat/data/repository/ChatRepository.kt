package com.example.streamchat.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.streamchat.R
import com.example.streamchat.data.model.BotResponse
import com.example.streamchat.data.model.Event
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    // EVENT MANAGEMENT

    // Create a new event
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun createEvent(
        eventName: String,
        description: String = "",
        eventDate: Long? = null,
        coverImage: String = "",
        firebaseIdToken: String
    ): com.example.streamchat.data.model.CreateEventResponse = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/events/create"

        val currentUser = getCurrentUser()
            ?: throw IllegalStateException("User not logged in")

        val requestBody = JSONObject().apply {
            put("eventName", eventName)
            put("description", description)
            if (eventDate != null) put("eventDate", eventDate)
            put("coverImage", coverImage)
            put("adminUserId", currentUser.id)
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response")

            if (!resp.isSuccessful) {
                // Try to parse as JSON error, otherwise use plain text
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optString("error", body)
                } catch (e: Exception) {
                    body
                }
                throw IllegalStateException("Server error (${resp.code}): $errorMsg")
            }

            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IllegalStateException("Invalid JSON response: $body")
            }

            com.example.streamchat.data.model.CreateEventResponse(
                success = json.optBoolean("success", false),
                eventId = json.optString("eventId"),
                joinLink = json.optString("joinLink"),
                channelId = json.optString("channelId"),
                channelCid = json.optString("channelCid")
            )
        }
    }

    // Join an event
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun joinEvent(
        eventId: String,
        firebaseIdToken: String
    ): com.example.streamchat.data.model.JoinEventResponse = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/events/join"

        val currentUser = getCurrentUser()
            ?: throw IllegalStateException("User not logged in")

        val requestBody = JSONObject().apply {
            put("eventId", eventId)
            put("userId", currentUser.id)
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response")

            if (!resp.isSuccessful) {
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optString("error", body)
                } catch (e: Exception) {
                    body
                }
                throw IllegalStateException("Server error (${resp.code}): $errorMsg")
            }

            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IllegalStateException("Invalid JSON response: $body")
            }

            com.example.streamchat.data.model.JoinEventResponse(
                success = json.optBoolean("success", false),
                channelId = json.optString("channelId"),
                channelCid = json.optString("channelCid")
            )
        }
    }

    // Get event details
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun getEventDetails(
        eventId: String,
        firebaseIdToken: String
    ): com.example.streamchat.data.model.EventDetailsResponse = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/events/$eventId"

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response")

            if (!resp.isSuccessful) {
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optString("error", body)
                } catch (e: Exception) {
                    body
                }
                throw IllegalStateException("Server error (${resp.code}): $errorMsg")
            }

            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IllegalStateException("Invalid JSON response: $body")
            }

            val eventJson = json.optJSONObject("event")
            val event = if (eventJson != null) {
                com.example.streamchat.data.model.Event(
                    id = eventJson.optString("id"),
                    name = eventJson.optString("name"),
                    description = eventJson.optString("description", ""),
                    adminUserId = eventJson.optString("adminUserId"),
                    eventDate = if (eventJson.has("eventDate")) eventJson.optLong("eventDate") else null,
                    coverImage = eventJson.optString("coverImage", ""),
                    joinLink = eventJson.optString("joinLink"),
                    channelId = eventJson.optString("id"),
                    memberCount = eventJson.optInt("memberCount", 0),
                    createdAt = eventJson.optString("createdAt", "")
                )
            } else null

            com.example.streamchat.data.model.EventDetailsResponse(
                success = json.optBoolean("success", false),
                event = event
            )
        }
    }

    // Delete a message using the backend token-server (server-side delete)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun deleteMessageOnServer(firebaseIdToken: String, messageId: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/messages/delete"

        val requestBody = JSONObject().apply {
            put("messageId", messageId)
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val errorMsg = try { JSONObject(body).optString("error", body) } catch (e: Exception) { body }
                throw IllegalStateException("Server error (${resp.code}): $errorMsg")
            }
            return@withContext try {
                val json = JSONObject(body)
                json.optBoolean("success", false)
            } catch (e: Exception) {
                true
            }
        }
    }

    // Store/retrieve pending event link for post-login join
    fun savePendingEventLink(link: String) {
        prefs.edit().putString(KEY_PENDING_EVENT, link).apply()
    }

    fun getPendingEventLink(): String? {
        return prefs.getString(KEY_PENDING_EVENT, null)
    }

    fun clearPendingEventLink() {
        prefs.edit().remove(KEY_PENDING_EVENT).apply()
    }

    //  AI CHATBOT INTEGRATION


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun sendMessageToBot(
        message: String,
        channelId: String,
        channelType: String = "messaging",
        firebaseIdToken: String
    ): BotResponse = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/chat/bot"

        val currentUser = getCurrentUser()
            ?: throw IllegalStateException("User not logged in")

        val requestBody = JSONObject().apply {
            put("message", message)
            put("channelId", channelId)
            put("channelType", channelType)
            put("userId", currentUser.id)
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response")

            if (!resp.isSuccessful) {
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optString("error", body)
                } catch (e: Exception) {
                    body
                }
                throw IllegalStateException("Bot error (${resp.code}): $errorMsg")
            }

            val json = JSONObject(body)
            BotResponse(
                success = json.optBoolean("success", false),
                reply = json.optString("reply", "")
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "stream_chat_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_IMAGE = "user_image"
        private const val KEY_TOKEN = "user_token"
        private const val KEY_PENDING_EVENT = "pending_event_link"

        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun getAllEvents(firebaseIdToken: String): List<Event> = withContext(Dispatchers.IO) {
        val baseUrl = appContext.getString(R.string.backend_base_url).trimEnd('/')
        val url = "$baseUrl/events/all"

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $firebaseIdToken")
            .get()
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: throw IllegalStateException("Empty response")

                if (!resp.isSuccessful) {
                    val errorMsg = try {
                        val json = JSONObject(body)
                        json.optString("error", body)
                    } catch (e: Exception) {
                        body
                    }
                    throw IllegalStateException("Server error (${resp.code}): $errorMsg")
                }

                val json = JSONObject(body)
                val eventsArray = json.optJSONArray("events") ?: return@withContext emptyList<Event>()

                val events = mutableListOf<Event>()
                for (i in 0 until eventsArray.length()) {
                    val e = eventsArray.getJSONObject(i)
                    val event = Event(
                        id = e.optString("id"),
                        name = e.optString("name"),
                        description = e.optString("description", ""),
                        adminUserId = e.optString("adminUserId"),
                        eventDate = if (e.has("eventDate")) e.optLong("eventDate") else null,
                        coverImage = e.optString("coverImage", ""),
                        joinLink = e.optString("joinLink"),
                        channelId = e.optString("channelId"),
                        channelCid = e.optString("channelCid"),
                        memberCount = e.optInt("memberCount", 0),
                        createdAt = e.optString("createdAt", "")
                    )
                    events.add(event)
                }

                events
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<Event>()
        }
    }


}
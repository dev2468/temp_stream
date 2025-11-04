package com.example.streamchat

import android.app.Application
import com.example.streamchat.data.repository.ChatRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import io.getstream.chat.android.offline.plugin.factory.StreamOfflinePluginFactory
import io.getstream.chat.android.state.plugin.config.StatePluginConfig
import io.getstream.chat.android.state.plugin.factory.StreamStatePluginFactory

class ChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase (google-services plugin will auto-init if google-services.json is present)
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
            // Safe to ignore if already initialized
        }
        initializeChatClient()
        reconnectUserIfNeeded()
    }

    private fun initializeChatClient() {
        val offlinePluginFactory = StreamOfflinePluginFactory(appContext = applicationContext)
        val statePluginFactory = StreamStatePluginFactory(
            config = StatePluginConfig(),
            appContext = this
        )

        val apiKey = getString(R.string.stream_api_key)
        ChatClient.Builder(apiKey, applicationContext)
            .withPlugins(offlinePluginFactory, statePluginFactory)
            .logLevel(ChatLogLevel.ALL)
            .build()
    }

    private fun reconnectUserIfNeeded() {
        val repository = ChatRepository.getInstance(applicationContext)
        val user = repository.getCurrentUser()
        val token = repository.getToken()

        if (user != null && !token.isNullOrBlank()) {
            ChatClient.instance().connectUser(user, token).enqueue { result ->
                if (result.isSuccess) {
                    android.util.Log.d("ChatApp", "✅ Reconnected saved user: ${user.id}")
                } else {
                    android.util.Log.e("ChatApp", "❌ Failed to reconnect: ${result.errorOrNull()?.message}")
                    repository.clearSession()
                }
            }
        } else {
            android.util.Log.d("ChatApp", "ℹ️ No saved user session found.")
        }
    }
}

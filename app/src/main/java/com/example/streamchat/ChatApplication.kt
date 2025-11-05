package com.example.streamchat

import android.app.Application
import com.example.streamchat.data.repository.ChatRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import io.getstream.chat.android.offline.plugin.factory.StreamOfflinePluginFactory
import io.getstream.chat.android.state.plugin.config.StatePluginConfig
import io.getstream.chat.android.state.plugin.factory.StreamStatePluginFactory
import io.getstream.chat.android.client.socket.SocketListener
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

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
        logConnectivityDiagnostics()
        reconnectUserIfNeeded()
    }

    private fun initializeChatClient() {
        val offlinePluginFactory = StreamOfflinePluginFactory(appContext = applicationContext)
        val statePluginFactory = StreamStatePluginFactory(
            config = StatePluginConfig(),
            appContext = this
        )

        val apiKey = getString(R.string.stream_api_key)
        val client = ChatClient.Builder(apiKey, applicationContext)
            .withPlugins(offlinePluginFactory, statePluginFactory)
            .logLevel(ChatLogLevel.ALL)
            .build()

        // Add a socket listener for detailed WS diagnostics in Logcat
        client.addSocketListener(object : SocketListener() {
            override fun onConnecting() {
                android.util.Log.d("ChatWS", "Connecting to Stream WebSocket…")
            }

            override fun onConnected(event: io.getstream.chat.android.client.events.ConnectedEvent) {
                android.util.Log.d("ChatWS", "Connected. Health: ${event.me?.id} cid: ${event.connectionId}")
            }

            override fun onDisconnected(cause: io.getstream.chat.android.client.clientstate.DisconnectCause) {
                val causeLabel = cause::class.java.simpleName
                android.util.Log.w("ChatWS", "Disconnected. Cause=$causeLabel")
            }
        })
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

    private fun logConnectivityDiagnostics() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            // Dump current network state
            val active = cm.activeNetwork
            val caps = if (active != null) cm.getNetworkCapabilities(active) else null
            val transports = mutableListOf<String>()
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("WIFI")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("CELLULAR")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ETHERNET")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("VPN")
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) transports.add("INTERNET")
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) transports.add("VALIDATED")
            }
            android.util.Log.d("NetDiag", "ActiveNetwork=${active != null} transports=${transports.joinToString(",")} ")

            // Log connectivity changes (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        android.util.Log.d("NetDiag", "Network available: $network")
                    }

                    override fun onLost(network: Network) {
                        android.util.Log.d("NetDiag", "Network lost: $network")
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        val flags = buildString {
                            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) append("WIFI ")
                            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) append("CELLULAR ")
                            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) append("ETHERNET ")
                            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) append("VPN ")
                            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) append("INTERNET ")
                            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) append("VALIDATED ")
                        }
                        android.util.Log.d("NetDiag", "Capabilities changed: $flags")
                    }
                })
            }
        } catch (_: Exception) {
            // Diagnostics are best-effort only
        }
    }
}

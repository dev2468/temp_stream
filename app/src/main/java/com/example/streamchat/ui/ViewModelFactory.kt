package com.example.streamchat.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.auth.FirebaseAuthViewModel
import com.example.streamchat.ui.channels.ChannelListViewModel
import com.example.streamchat.ui.friends.FriendListViewModel
import com.example.streamchat.ui.login.LoginViewModel
import com.example.streamchat.ui.messages.MessageListViewModel
import io.getstream.chat.android.client.ChatClient

class ViewModelFactory(
    private val chatClient: ChatClient,
    private val repository: ChatRepository,
    private val channelId: String? = null,
    private val context: Context? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(repository, chatClient) as T
            }
            modelClass.isAssignableFrom(FirebaseAuthViewModel::class.java) -> {
                FirebaseAuthViewModel(repository, chatClient) as T
            }
            modelClass.isAssignableFrom(ChannelListViewModel::class.java) -> {
                ChannelListViewModel(chatClient) as T
            }
            modelClass.isAssignableFrom(MessageListViewModel::class.java) -> {
                require(channelId != null) { "channelId is required for MessageListViewModel" }
                require(context != null) { "context is required for MessageListListViewModel" }
                MessageListViewModel(chatClient, channelId, context) as T
            }
            modelClass.isAssignableFrom(FriendListViewModel::class.java) -> {
                FriendListViewModel(chatClient) as T
            }
            modelClass.isAssignableFrom(com.example.streamchat.ui.events.EventListViewModel::class.java) -> {
                com.example.streamchat.ui.events.EventListViewModel(repository) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

package com.example.streamchat.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.client.api.models.QueryUsersRequest
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class FriendListUiState {
    object Loading : FriendListUiState()
    data class Success(val users: List<User>) : FriendListUiState()
    data class Error(val message: String) : FriendListUiState()
}

class FriendListViewModel(private val chatClient: ChatClient) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendListUiState>(FriendListUiState.Loading)
    val uiState: StateFlow<FriendListUiState> = _uiState

    fun loadFriends() {
        viewModelScope.launch {
            try {
                _uiState.value = FriendListUiState.Loading

                val currentUser = chatClient.getCurrentUser() ?: return@launch
                val myId = currentUser.id

                // Fetch all users except current one
                val allUsersResponse = chatClient.queryUsers(
                    QueryUsersRequest(filter = Filters.ne("id", myId), offset = 0, limit = 50)
                ).await()

                if (!allUsersResponse.isSuccess) {
                    _uiState.value = FriendListUiState.Error("Failed to load users")
                    return@launch
                }

                val allUsers = allUsersResponse.getOrThrow()

                // Fetch channels to identify existing chats
                val channelsResponse = chatClient.queryChannels(
                    QueryChannelsRequest(
                        filter = Filters.and(
                            Filters.eq("type", "messaging"),
                            Filters.`in`("members", listOf(myId))
                        ),
                        offset = 0,
                        limit = 50
                    )
                ).await()

                val existingIds = if (channelsResponse.isSuccess)
                    channelsResponse.getOrThrow().flatMap { it.members.map { it.user.id } }.toSet()
                else emptySet()

                val newFriends = allUsers.filterNot { it.id in existingIds }
                _uiState.value = FriendListUiState.Success(newFriends)
            } catch (e: Exception) {
                _uiState.value = FriendListUiState.Error(e.message ?: "Unexpected error")
            }
        }
    }

    fun createPrivateChannel(user: User, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val currentUser = chatClient.getCurrentUser() ?: return@launch
                val members = listOf(currentUser.id, user.id).sorted()
                val channelId = members.joinToString("-")
                val result = chatClient.createChannel(
                    channelType = "messaging",
                    channelId = channelId,
                    memberIds = members,
                    extraData = emptyMap()
                ).await()
                onResult(if (result.isSuccess) result.getOrThrow().cid else null)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}

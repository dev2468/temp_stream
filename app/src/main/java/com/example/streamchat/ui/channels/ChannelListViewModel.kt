package com.example.streamchat.ui.channels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.client.api.models.QueryUsersRequest
import io.getstream.chat.android.client.events.ChannelUpdatedEvent
import io.getstream.chat.android.client.events.MessageReadEvent
import io.getstream.chat.android.client.events.NewMessageEvent
import io.getstream.chat.android.client.events.NotificationAddedToChannelEvent
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChannelListViewModel(
    private val chatClient: ChatClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChannelListUiState>(ChannelListUiState.Loading)
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()
    // Optional LiveData mirror for XML/LiveData observers and Compose observeAsState()
    val uiStateLiveData: LiveData<ChannelListUiState> = _uiState.asLiveData()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val searchQueryLiveData: LiveData<String> = _searchQuery.asLiveData()

    // Users that already exist in this Stream app (excluding current user)
    private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    val availableUsers: StateFlow<List<User>> = _availableUsers.asStateFlow()
    val availableUsersLiveData: LiveData<List<User>> = _availableUsers.asLiveData()

    // Debounced refresh to avoid rate limiting
    // IMPORTANT: Declare before init block since it's used during init
    private val _refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    init {
        loadChannels()
        refreshAvailableUsers()
        subscribeToChannelEvents()
        collectRefreshSignals()
    }

    private fun subscribeToChannelEvents() {
        // Subscribe to events that affect channel list
        chatClient.subscribe { event ->
            when (event) {
                is NewMessageEvent,
                is MessageReadEvent,
                is ChannelUpdatedEvent,
                is NotificationAddedToChannelEvent -> {
                    // Signal a refresh; actual loading is debounced
                    _refreshSignals.tryEmit(Unit)
                }
                else -> Unit
            }
        }
    }

    private fun collectRefreshSignals() {
        _refreshSignals
            .debounce(1000)
            .onEach {
                if (_searchQuery.value.isBlank()) {
                    loadChannels()
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadChannels(query: String = "") {
        viewModelScope.launch {
            _uiState.value = ChannelListUiState.Loading

            try {
                val currentUserId = chatClient.getCurrentUser()?.id ?: return@launch
                
                val filter = if (query.isBlank()) {
                    Filters.and(
                        Filters.eq("type", "messaging"),
                        Filters.`in`("members", listOf(currentUserId))
                    )
                } else {
                    Filters.and(
                        Filters.eq("type", "messaging"),
                        Filters.`in`("members", listOf(currentUserId)),
                        Filters.autocomplete("name", query)
                    )
                }

                val request = QueryChannelsRequest(
                    filter = filter,
                    offset = 0,
                    limit = 30
                ).apply {
                    watch = true
                    state = true
                }

                val result = chatClient.queryChannels(request).await()
                if (result.isSuccess) {
                    val channels = result.getOrThrow()
                    _uiState.value = if (channels.isEmpty()) {
                        ChannelListUiState.Empty
                    } else {
                        ChannelListUiState.Success(channels)
                    }
                } else {
                    _uiState.value = ChannelListUiState.Error(result.errorOrNull()?.message ?: "Failed to load channels")
                }
            } catch (e: Exception) {
                _uiState.value = ChannelListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        loadChannels(query)
    }
    
    fun createChannel(memberIds: List<String>, groupName: String? = null) {
        viewModelScope.launch {
            try {
                val currentUserId = chatClient.getCurrentUser()?.id ?: return@launch
                val allMemberIds = (memberIds + currentUserId).distinct()
                
                // Decide channel id strategy:
                // - For 1:1 chats, use deterministic id from member ids to avoid duplicates
                // - For group chats (2+ others), use a random id to allow multiple groups with same members
                val channelId = if (allMemberIds.size <= 2) {
                    allMemberIds.sorted().joinToString("-")
                } else {
                    java.util.UUID.randomUUID().toString()
                }

                // Preflight: ensure all selected members exist
                val missing = findMissingUsers(allMemberIds - currentUserId)
                if (missing.isNotEmpty()) {
                    _uiState.value = ChannelListUiState.Error(
                        "Cannot create channel. These users don't exist yet: ${missing.joinToString(", ")}. Ask them to log in once or create them in your Stream Dashboard."
                    )
                    return@launch
                }
                val extraData = buildMap<String, Any> {
                    val name = groupName?.trim().orEmpty()
                    if (name.isNotEmpty()) put("name", name)
                }

                val result = chatClient.createChannel(
                    channelType = "messaging",
                    channelId = channelId,
                    memberIds = allMemberIds,
                    extraData = extraData
                ).await()
                
                if (result.isSuccess) {
                    // Reload channels to show the new one
                    loadChannels()
                    // Also refresh the available users list in case new users appeared
                    refreshAvailableUsers()
                } else {
                    _uiState.value = ChannelListUiState.Error(result.errorOrNull()?.message ?: "Failed to create channel")
                }
            } catch (e: Exception) {
                _uiState.value = ChannelListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshAvailableUsers() {
        viewModelScope.launch {
            try {
                val currentUserId = chatClient.getCurrentUser()?.id ?: return@launch
                val filter = Filters.ne("id", currentUserId)
                val request = QueryUsersRequest(filter = filter, offset = 0, limit = 50)
                val response = chatClient.queryUsers(request).await()
                if (response.isSuccess) {
                    _availableUsers.value = response.getOrThrow()
                }
            } catch (_: Exception) {
                // Ignore; keep previous list
            }
        }
    }

    private suspend fun findMissingUsers(userIds: List<String>): List<String> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            val filter = Filters.`in`("id", userIds)
            val request = QueryUsersRequest(filter = filter, offset = 0, limit = userIds.size)
            val response = chatClient.queryUsers(request).await()
            if (response.isSuccess) {
                val existing = response.getOrThrow().map { it.id }.toSet()
                userIds.filterNot { it in existing }
            } else {
                // If the check fails, fall back to attempting creation (server will validate)
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

sealed class ChannelListUiState {
    object Loading : ChannelListUiState()
    object Empty : ChannelListUiState()
    data class Success(val channels: List<Channel>) : ChannelListUiState()
    data class Error(val message: String) : ChannelListUiState()
}

package com.example.streamchat.ui.messages

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.events.MessageReadEvent
import io.getstream.chat.android.client.events.NewMessageEvent
import io.getstream.chat.android.client.events.ReactionNewEvent
import io.getstream.chat.android.client.events.ReactionDeletedEvent
import io.getstream.chat.android.models.Attachment
import io.getstream.chat.android.models.Message
import io.getstream.chat.android.models.Reaction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class MessageListViewModel(
    private val chatClient: ChatClient,
    private val channelId: String,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessageListUiState>(MessageListUiState.Loading)
    val uiState: StateFlow<MessageListUiState> = _uiState.asStateFlow()
    // Optional LiveData mirror for XML/LiveData observers and Compose observeAsState()
    val uiStateLiveData: LiveData<MessageListUiState> = _uiState.asLiveData()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()
    val messageTextLiveData: LiveData<String> = _messageText.asLiveData()

    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()
    val selectedImagesLiveData: LiveData<List<Uri>> = _selectedImages.asLiveData()
    
    // Track if user can send messages (for event channels)
    private val _canSendMessages = MutableStateFlow(true)
    val canSendMessages: StateFlow<Boolean> = _canSendMessages.asStateFlow()
    val canSendMessagesLiveData: LiveData<Boolean> = _canSendMessages.asLiveData()

    private val channelClient = chatClient.channel(channelId)

    // Debounced refresh control to prevent hitting rate limits
    // IMPORTANT: Declare before init block since it's used during init
    private val _refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    init {
        loadMessages()
        subscribeToMessageEvents()
        collectRefreshSignals()
    }

    private fun subscribeToMessageEvents() {
        // Subscribe to events for this specific channel
        chatClient.subscribe { event ->
            when (event) {
                is NewMessageEvent -> {
                    if (event.cid == channelId) {
                        _refreshSignals.tryEmit(Unit)
                    }
                }
                is ReactionNewEvent -> {
                    if (event.cid == channelId) {
                        _refreshSignals.tryEmit(Unit)
                    }
                }
                is ReactionDeletedEvent -> {
                    if (event.cid == channelId) {
                        _refreshSignals.tryEmit(Unit)
                    }
                }
                is MessageReadEvent -> {
                    if (event.cid == channelId) {
                        _refreshSignals.tryEmit(Unit)
                    }
                }
                else -> {
                    // Ignore other events
                }
            }
        }
    }

    private fun collectRefreshSignals() {
        _refreshSignals
            .debounce(750)
            .onEach { loadMessages() }
            .launchIn(viewModelScope)
    }

    private fun loadMessages() {
        viewModelScope.launch {
            try {
                val result = channelClient.watch().await()
                if (result.isSuccess) {
                    val channel = result.getOrThrow()
                    
                    // Check if this is an event channel and if user is admin
                    val isEventChannel = channel.type == "event"
                    if (isEventChannel) {
                        val eventAdmin = channel.extraData["event_admin"] as? String
                        val currentUserId = chatClient.getCurrentUser()?.id
                        _canSendMessages.value = currentUserId == eventAdmin
                    } else {
                        _canSendMessages.value = true
                    }
                    
                    _uiState.value = MessageListUiState.Success(
                        messages = channel.messages,
                        channelName = channel.name.ifEmpty { "Chat" },
                        typingUsers = emptyList()
                    )
                    channelClient.markRead().enqueue()
                } else {
                    _uiState.value = MessageListUiState.Error(result.errorOrNull()?.message ?: "Failed to load messages")
                }
            } catch (e: Exception) {
                _uiState.value = MessageListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
        if (text.isNotEmpty()) {
            channelClient.keystroke().enqueue()
        } else {
            channelClient.stopTyping().enqueue()
        }
    }

    fun sendMessage() {
        val text = _messageText.value
        val images = _selectedImages.value

        if (text.isBlank() && images.isEmpty()) return

        viewModelScope.launch {
            try {
                val message = Message(
                    cid = channelId,
                    text = text,
                    attachments = images.mapNotNull { uri ->
                        uriToFile(uri)?.let { file ->
                            Attachment(upload = file, type = "image")
                        }
                    }.toMutableList()
                )

                val result = channelClient.sendMessage(message).await()
                if (result.isSuccess) {
                    _messageText.value = ""
                    _selectedImages.value = emptyList()
                    channelClient.stopTyping().enqueue()
                    // No need to manually reload, observeChannelState will handle it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addReaction(messageId: String, reactionType: String) {
        viewModelScope.launch {
            try {
                channelClient.sendReaction(
                    reaction = Reaction(messageId = messageId, type = reactionType, score = 1),
                    enforceUnique = true
                ).await()
                // No need to manually reload, observeChannelState will handle it
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                channelClient.deleteMessage(messageId).await()
                // No need to manually reload, observeChannelState will handle it
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        _selectedImages.value = _selectedImages.value + uris
    }

    fun removeImage(uri: Uri) {
        _selectedImages.value = _selectedImages.value - uri
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

sealed class MessageListUiState {
    object Loading : MessageListUiState()
    data class Success(
        val messages: List<Message>,
        val channelName: String,
        val typingUsers: List<String>
    ) : MessageListUiState()
    data class Error(val message: String) : MessageListUiState()
}

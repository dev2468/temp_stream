package com.example.streamchat.ui.messages

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.streamchat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.events.MessageReadEvent
import io.getstream.chat.android.client.events.NewMessageEvent
import io.getstream.chat.android.client.events.ReactionNewEvent
import io.getstream.chat.android.client.events.ReactionDeletedEvent
import io.getstream.chat.android.client.events.TypingStartEvent
import io.getstream.chat.android.client.events.TypingStopEvent
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
import android.util.Log
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date

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
    
    // Reply-to state: holds (username, previewText)
    private val _replyPreview = MutableStateFlow<Pair<String, String>?>(null)
    val replyPreviewLiveData: LiveData<Pair<String, String>?> = _replyPreview.asLiveData()
    private var _replyToMessageId: String? = null
    
    // Track if user can send messages (for event channels)
    private val _canSendMessages = MutableStateFlow(true)
    val canSendMessages: StateFlow<Boolean> = _canSendMessages.asStateFlow()
    val canSendMessagesLiveData: LiveData<Boolean> = _canSendMessages.asLiveData()

    private val channelClient = chatClient.channel(channelId)
    private val repository = ChatRepository.getInstance(context)

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
                is TypingStartEvent -> {
                    if (event.cid == channelId) {
                        // update typing users
                        val current = (_uiState.value as? MessageListUiState.Success)?.typingUsers?.toMutableList() ?: mutableListOf()
                        val name = event.user?.name ?: event.user?.id ?: "Someone"
                        if (!current.contains(name)) current.add(name)
                        val curState = _uiState.value
                        if (curState is MessageListUiState.Success) {
                            _uiState.value = curState.copy(typingUsers = current)
                        }
                    }
                }
                is TypingStopEvent -> {
                    if (event.cid == channelId) {
                        val current = (_uiState.value as? MessageListUiState.Success)?.typingUsers?.toMutableList() ?: mutableListOf()
                        val name = event.user?.name ?: event.user?.id ?: "Someone"
                        current.remove(name)
                        val curState = _uiState.value
                        if (curState is MessageListUiState.Success) {
                            _uiState.value = curState.copy(typingUsers = current)
                        }
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
                    val isEventChannel = channel.extraData["is_event_channel"] as? Boolean == true
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

        // Check if message starts with @bot (mention-based bot trigger)
        val isBotMention = text.trim().startsWith("@bot ", ignoreCase = true)
        
        if (isBotMention && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Extract the actual message after @bot
            val botMessage = text.trim().removePrefix("@bot ").removePrefix("@Bot ").trim()
            if (botMessage.isNotEmpty()) {
                sendBotMessage(botMessage)
                return
            }
        }

        // Regular message sending
        viewModelScope.launch {
            try {
                // If replying, prefix message text with small quoted context
                val finalText = if (_replyToMessageId != null && _replyPreview.value != null) {
                    val (author, preview) = _replyPreview.value!!
                    "↪ $author: $preview\n$text"
                } else text

                val message = Message(
                    cid = channelId,
                    text = finalText,
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
                    // clear reply state on success
                    _replyPreview.value = null
                    _replyToMessageId = null
                    // No need to manually reload, observeChannelState will handle it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setReplyToMessage(message: Message) {
        _replyToMessageId = message.id
        val name = message.user.name.ifBlank { message.user.id }
        val preview = message.text.take(80)
        _replyPreview.value = Pair(name, preview)
    }

    fun clearReply() {
        _replyToMessageId = null
        _replyPreview.value = null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sendBotMessage(userMessage: String) {
        viewModelScope.launch {
            try {
                // First, send user's message to the channel
                val userMsg = Message(
                    cid = channelId,
                    text = "@bot $userMessage"
                )
                channelClient.sendMessage(userMsg).await()
                
                // Clear input
                _messageText.value = ""
                channelClient.stopTyping().enqueue()
                
                // Show "AI Bot is typing..." indicator
                // (This happens automatically when backend sends typing event)
                
                // Get Firebase ID token
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser == null) {
                    println("Bot error: User not authenticated")
                    return@launch
                }
                
                val idToken = firebaseUser.getIdToken(false).await().token
                if (idToken == null) {
                    println("Bot error: Failed to get ID token")
                    return@launch
                }
                
                // Extract channel type and ID from channelId (format: "type:id")
                val parts = channelId.split(":")
                val channelType = if (parts.size >= 2) parts[0] else "messaging"
                val actualChannelId = if (parts.size >= 2) parts[1] else channelId
                
                // Call bot API
                repository.sendMessageToBot(
                    message = userMessage,
                    channelId = actualChannelId,
                    channelType = channelType,
                    firebaseIdToken = idToken
                )
                
                // Bot response will appear automatically via Stream Chat SDK
                // No need to manually refresh - the NewMessageEvent will trigger
            } catch (e: Exception) {
                e.printStackTrace()
                println("Bot error: ${e.message}")
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
                Log.d("MessageListViewModel", "Attempting to delete message (server): $messageId")

                // Try server-side deletion via backend token-server using Firebase ID token
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val idToken = firebaseUser?.getIdToken(false)?.await()?.token

                val deletedOnServer = if (idToken != null) {
                    try {
                        repository.deleteMessageOnServer(idToken, messageId)
                    } catch (e: Exception) {
                        Log.w("MessageListViewModel", "Server delete failed, falling back to client delete: ${e.message}")
                        false
                    }
                } else false

                if (deletedOnServer) {
                    Log.d("MessageListViewModel", "Server delete succeeded for: $messageId")
                    val cur = _uiState.value
                    if (cur is MessageListUiState.Success) {
                        _uiState.value = cur.copy(messages = cur.messages.filter { it.id != messageId })
                    }
                } else {
                    // Fall back to client delete (may fail due to permissions)
                    Log.d("MessageListViewModel", "Attempting client-side delete for: $messageId")
                    val result = channelClient.deleteMessage(messageId).await()
                    if (result.isSuccess) {
                        Log.d("MessageListViewModel", "Client delete succeeded for: $messageId")
                        val cur = _uiState.value
                        if (cur is MessageListUiState.Success) {
                            _uiState.value = cur.copy(messages = cur.messages.filter { it.id != messageId })
                        }
                    } else {
                        Log.w("MessageListViewModel", "Client delete failed for $messageId: ${result.errorOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageListViewModel", "Exception deleting message $messageId", e)
            }
        }
    }

    /**
     * Mark the message as locally deleted (remove from UI) without calling server delete yet.
     * This lets us show an immediate remove and allow Undo before finalizing on server.
     */
    fun markMessageLocallyDeleted(messageId: String) {
        val cur = _uiState.value
        if (cur is MessageListUiState.Success) {
            _uiState.value = cur.copy(messages = cur.messages.filter { it.id != messageId })
        }
    }

    /**
     * Restore a locally removed message back into the UI (used when Undo is pressed).
     */
    fun restoreLocalMessage(message: Message) {
        val cur = _uiState.value
        if (cur is MessageListUiState.Success) {
            val merged = (cur.messages + message).sortedBy { it.createdAt ?: Date(0) }
            _uiState.value = cur.copy(messages = merged)
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

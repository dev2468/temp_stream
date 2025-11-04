package com.example.streamchat.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.getstream.chat.android.client.ChatClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AppUser(
    val uid: String = "",
    val username: String = "",
    val email: String = ""
)

sealed class FriendListUiState {
    object Loading : FriendListUiState()
    data class Success(
        val friends: List<AppUser> = emptyList(),
        val requests: List<AppUser> = emptyList()
    ) : FriendListUiState()
    data class Error(val message: String) : FriendListUiState()
}

class FriendListViewModel(chatClient: ChatClient) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<FriendListUiState>(FriendListUiState.Loading)
    val uiState: StateFlow<FriendListUiState> = _uiState.asStateFlow()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    // Load all friends + incoming requests
    fun loadFriendsAndRequests() {
        viewModelScope.launch {
            try {
                if (currentUid.isBlank()) return@launch

                combine(
                    observeFriends(currentUid),
                    observeRequests(currentUid)
                ) { friends, requests ->
                    FriendListUiState.Success(friends = friends, requests = requests)
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = FriendListUiState.Error(e.message ?: "Error loading friends")
            }
        }
    }

    private fun observeFriends(uid: String): Flow<List<AppUser>> = callbackFlow {
        val ref = firestore.collection("friends").document(uid).collection("list")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val friends = snapshot?.documents?.mapNotNull {
                val username = it.getString("username") ?: return@mapNotNull null
                val email = it.getString("email") ?: ""
                AppUser(uid = it.id, username = username, email = email)
            } ?: emptyList()
            trySend(friends)
        }
        awaitClose { listener.remove() }
    }

    private fun observeRequests(uid: String): Flow<List<AppUser>> = callbackFlow {
        val ref = firestore.collection("friend_requests").document(uid).collection("incoming")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val requests = snapshot?.documents?.mapNotNull {
                val username = it.getString("username") ?: return@mapNotNull null
                AppUser(uid = it.id, username = username)
            } ?: emptyList()
            trySend(requests)
        }
        awaitClose { listener.remove() }
    }

    // A sends request to B (by username)
    fun sendFriendRequest(receiverUsername: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val senderUid = currentUid
                val senderUsername = getCurrentUsername() ?: return@launch onResult(false, "User not logged in")

                val query = firestore.collection("users")
                    .whereEqualTo("username", receiverUsername.lowercase().trim())
                    .get().await()

                if (query.isEmpty) {
                    onResult(false, "User not found")
                    return@launch
                }

                val receiverDoc = query.documents.first()
                val receiverUid = receiverDoc.id

                if (receiverUid == senderUid) {
                    onResult(false, "You can't add yourself")
                    return@launch
                }

                val requestRef = firestore.collection("friend_requests")
                    .document(receiverUid)
                    .collection("incoming")
                    .document(senderUid)

                requestRef.set(
                    mapOf(
                        "username" to senderUsername,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()

                onResult(true, "Request sent to @$receiverUsername")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to send request")
            }
        }
    }

    // B accepts A’s request → both added as friends, request deleted
    fun acceptFriendRequest(senderUid: String, senderUsername: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val receiverUid = currentUid
                val receiverUsername = getCurrentUsername() ?: return@launch onDone(false)

                val senderRef = firestore.collection("friends").document(senderUid)
                    .collection("list").document(receiverUid)

                val receiverRef = firestore.collection("friends").document(receiverUid)
                    .collection("list").document(senderUid)

                val senderUserDoc = firestore.collection("users").document(senderUid).get().await()
                val receiverUserDoc = firestore.collection("users").document(receiverUid).get().await()

                val senderEmail = senderUserDoc.getString("email") ?: ""
                val receiverEmail = receiverUserDoc.getString("email") ?: ""

                // Add each other as friends
                senderRef.set(
                    mapOf("username" to receiverUsername, "email" to receiverEmail, "timestamp" to System.currentTimeMillis())
                ).await()
                receiverRef.set(
                    mapOf("username" to senderUsername, "email" to senderEmail, "timestamp" to System.currentTimeMillis())
                ).await()

                // Delete pending request
                firestore.collection("friend_requests").document(receiverUid)
                    .collection("incoming").document(senderUid).delete().await()

                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }

    // Remove friend (removes both sides)
    fun removeFriend(friendUid: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val myUid = currentUid

                firestore.collection("friends").document(myUid)
                    .collection("list").document(friendUid).delete().await()

                firestore.collection("friends").document(friendUid)
                    .collection("list").document(myUid).delete().await()

                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }

    private suspend fun getCurrentUsername(): String? {
        val uid = currentUid
        val doc = firestore.collection("users").document(uid).get().await()
        return doc.getString("username")
    }
}

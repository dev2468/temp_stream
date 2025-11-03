package com.example.streamchat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.streamchat.data.repository.ChatRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirebaseAuthViewModel(
    private val repository: ChatRepository,
    private val chatClient: ChatClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    val uiStateLiveData: LiveData<AuthUiState> = _uiState.asLiveData()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val user = auth.currentUser ?: throw IllegalStateException("No user after sign in")
                val idToken = user.getIdToken(true).await().token ?: throw IllegalStateException("No ID token")

                val fullName = user.displayName ?: email.substringBefore('@')
                val streamToken = repository.fetchTokenWithFirebaseAuth(idToken, user.uid, fullName)

                val streamUser = User(id = user.uid, name = fullName, image = user.photoUrl?.toString() ?: "")
                val result = withTimeout(15_000) { chatClient.connectUser(streamUser, streamToken).await() }
                if (result.isSuccess) {
                    repository.saveUser(streamUser, streamToken)
                    val tokenProvider = object : TokenProvider { override fun loadToken(): String = streamToken }
                    repository.setTokenProvider(tokenProvider)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error(result.errorOrNull()?.message ?: "Failed to connect to Stream")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = AuthUiState.Error("Connection timeout. Check your internet and try again.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Sign in failed")
            }
        }
    }

    fun signUp(email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                val user = auth.currentUser ?: throw IllegalStateException("No user after sign up")

                // Optionally update display name
                user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(fullName).build()).await()

                val idToken = user.getIdToken(true).await().token ?: throw IllegalStateException("No ID token")
                val streamToken = repository.fetchTokenWithFirebaseAuth(idToken, user.uid, fullName)

                val streamUser = User(id = user.uid, name = fullName, image = user.photoUrl?.toString() ?: "")
                val result = withTimeout(15_000) { chatClient.connectUser(streamUser, streamToken).await() }
                if (result.isSuccess) {
                    repository.saveUser(streamUser, streamToken)
                    val tokenProvider = object : TokenProvider { override fun loadToken(): String = streamToken }
                    repository.setTokenProvider(tokenProvider)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error(result.errorOrNull()?.message ?: "Failed to connect to Stream")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = AuthUiState.Error("Connection timeout. Check your internet and try again.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Sign up failed")
            }
        }
    }
}

sealed class AuthUiState {
    object Initial : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

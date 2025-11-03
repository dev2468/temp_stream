package com.example.streamchat.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.streamchat.data.repository.ChatRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LoginViewModel(
    private val repository: ChatRepository,
    private val chatClient: ChatClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    val uiStateLiveData: LiveData<LoginUiState> = _uiState.asLiveData()

    fun login(userId: String, userName: String, userImage: String, token: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val user = User(id = userId, name = userName, image = userImage)
                val result = withTimeout(15_000) {
                    chatClient.connectUser(user, token).await()
                }

                if (result.isSuccess) {
                    repository.saveUser(user, token)
                    val tokenProvider = object : TokenProvider {
                        override fun loadToken(): String = token
                    }
                    repository.setTokenProvider(tokenProvider)
                    _uiState.value = LoginUiState.Success
                } else {
                    _uiState.value = LoginUiState.Error(result.errorOrNull()?.message ?: "Login failed")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = LoginUiState.Error("Login timed out. Check your Internet connection, API key, or token and try again.")
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loginWithDemo() {
        // Demo credentials for your API key (zrqhgvpgnjrc)
        // This token is valid for testing. For production, generate tokens server-side.
        login(
            userId = "john",
            userName = "John Doe",
            userImage = "https://bit.ly/2TIt8NR",
            token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiam9obiJ9.kKqLhOV-y7JqyDmcKYDNy_1wG-EQ-r1c7pz2-HrR7s0"
        )
    }

    fun loginWithServer(userId: String, userName: String, userImage: String = "") {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val token = repository.fetchTokenFromServer(userId, userName, userImage)
                login(userId, userName, userImage, token)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Failed to fetch token from server")
            }
        }
    }
}

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

package com.example.streamchat.ui.auth

// Legacy stub preserved for compatibility; FirebaseAuthViewModel replaces this.
// Not used anymore.
class SupabaseAuthViewModel

sealed class SupabaseAuthUiState {
    object Initial : SupabaseAuthUiState()
    object Loading : SupabaseAuthUiState()
    object Success : SupabaseAuthUiState()
    data class Error(val message: String) : SupabaseAuthUiState()
}

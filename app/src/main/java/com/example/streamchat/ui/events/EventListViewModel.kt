package com.example.streamchat.ui.events

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamchat.data.model.Event
import com.example.streamchat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class EventListViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventListUiState>(EventListUiState.Loading)
    val uiState: StateFlow<EventListUiState> = _uiState

    init {
        loadEvents()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = EventListUiState.Loading
            try {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                val idToken = firebaseUser?.getIdToken(false)?.await()?.token
                    ?: throw Exception("User not authenticated")

                val events: List<Event> = repository.getAllEvents(firebaseIdToken = idToken)

                _uiState.value = if (events.isEmpty()) {
                    EventListUiState.Empty
                } else {
                    EventListUiState.Success(events)
                }
            } catch (e: Exception) {
                _uiState.value = EventListUiState.Error(e.message ?: "Failed to load events")
            }
        }
    }
}

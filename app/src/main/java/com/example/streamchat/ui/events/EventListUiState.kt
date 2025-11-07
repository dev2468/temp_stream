package com.example.streamchat.ui.events

import com.example.streamchat.data.model.Event

sealed class EventListUiState {
    object Loading : EventListUiState()
    data class Success(val events: List<Event>) : EventListUiState()
    data class Error(val message: String) : EventListUiState()
    object Empty : EventListUiState()
}

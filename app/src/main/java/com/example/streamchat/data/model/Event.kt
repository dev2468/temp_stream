package com.example.streamchat.data.model

data class Event(
    val id: String,
    val name: String,
    val description: String = "",
    val adminUserId: String,
    val eventDate: Long? = null,
    val coverImage: String = "",
    val joinLink: String,
    val channelId: String,
    val channelCid: String = "",
    val memberCount: Int = 0,
    val createdAt: String = ""
)

data class CreateEventRequest(
    val eventName: String,
    val description: String = "",
    val eventDate: Long? = null,
    val coverImage: String = "",
    val adminUserId: String
)

data class CreateEventResponse(
    val success: Boolean,
    val eventId: String?,
    val joinLink: String?,
    val channelId: String?,
    val channelCid: String?,
    val error: String? = null
)

data class JoinEventRequest(
    val eventId: String,
    val userId: String
)

data class JoinEventResponse(
    val success: Boolean,
    val channelId: String?,
    val channelCid: String?,
    val error: String? = null
)

data class EventDetailsResponse(
    val success: Boolean,
    val event: Event?,
    val error: String? = null
)

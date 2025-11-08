package com.example.streamchat.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object EventImageManager {

    private val storage = FirebaseStorage.getInstance()
    private val cache = mutableMapOf<String, String?>()

    /**
     * Upload event cover image to Firebase Storage as /event_covers/{eventId}.jpg
     * Returns the download URL.
     */
    suspend fun uploadEventCoverImage(imageUri: Uri, eventId: String): String? {
        return try {
            val ref = storage.reference.child("event_covers/$eventId.jpg")
            ref.putFile(imageUri).await()

            val url = ref.downloadUrl.await().toString()
            cache[eventId] = url
            Log.d("EventImageManager", "✅ Uploaded event cover for $eventId: $url")

            url
        } catch (e: Exception) {
            Log.e("EventImageManager", "❌ Failed to upload image: ${e.message}")
            null
        }
    }

    /**
     * Get event cover image URL directly from Firebase Storage.
     */
    suspend fun getEventCoverImage(eventId: String): String? {
        cache[eventId]?.let { return it }

        return try {
            val ref = storage.reference.child("event_covers/$eventId.jpg")
            val url = ref.downloadUrl.await().toString()
            cache[eventId] = url
            Log.d("EventImageManager", "✅ Fetched event cover for $eventId: $url")
            url
        } catch (e: Exception) {
            Log.e("EventImageManager", "❌ Failed to fetch event cover for $eventId: ${e.message}")
            null
        }
    }
}

package com.example.streamchat.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object EventImageManager {

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val imageCache = mutableMapOf<String, String?>()

    /**
     * Uploads an event cover image to Firebase Storage and returns its public download URL.
     * Optionally stores it in Firestore under /events/{eventId}/coverImageUrl.
     */
    suspend fun uploadEventCoverImage(
        imageUri: Uri,
        eventId: String? = null
    ): String? {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"
            val filename = "event_covers/${uid}_${UUID.randomUUID()}.jpg"

            // Upload to Firebase Storage
            val ref = storage.reference.child(filename)
            ref.putFile(imageUri).await()

            // Get download URL
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d("EventImageManager", "✅ Uploaded event cover: $downloadUrl")

            // Cache result
            imageCache[filename] = downloadUrl

            // Store in Firestore if eventId is provided
            eventId?.let {
                firestore.collection("events").document(it)
                    .set(mapOf("coverImageUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }

            downloadUrl
        } catch (e: Exception) {
            Log.e("EventImageManager", "❌ Failed to upload image: ${e.message}")
            null
        }
    }

    /**
     * Fetch cover image for an event (cached lookup).
     */
    suspend fun getEventCoverImage(eventId: String): String? {
        imageCache[eventId]?.let { return it }
        return try {
            val snapshot = firestore.collection("events").document(eventId).get().await()
            val url = snapshot.getString("coverImageUrl")
            imageCache[eventId] = url
            url
        } catch (e: Exception) {
            Log.e("EventImageManager", "❌ Error fetching cover image: ${e.message}")
            null
        }
    }
}

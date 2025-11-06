package com.example.streamchat.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object UserProfileManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val userCache = mutableMapOf<String, String?>()

    suspend fun getUserProfileImage(uid: String): String? {
        // Return cached version if available
        userCache[uid]?.let { return it }

        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            val url = snapshot.getString("profileImageUrl")
            userCache[uid] = url
            url
        } catch (e: Exception) {
            Log.e("UserProfileManager", "Error fetching user image: ${e.message}")
            null
        }
    }

    suspend fun getUsername(uid: String): String? {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.getString("username")
        } catch (e: Exception) {
            Log.e("UserProfileManager", "Error fetching username: ${e.message}")
            null
        }
    }
}

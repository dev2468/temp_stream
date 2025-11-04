package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var usernameText: TextView
    private lateinit var btnChangePassword: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        nameText = findViewById(R.id.tvName)
        emailText = findViewById(R.id.tvEmail)
        usernameText = findViewById(R.id.tvUsername)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnLogout = findViewById(R.id.btnLogout)

        val user = auth.currentUser
        updateUserData(user)

        btnChangePassword.setOnClickListener {
            user?.email?.let { email ->
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to send reset email", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun updateUserData(user: FirebaseUser?) {
        if (user != null) {
            nameText.text = "Name: ${user.displayName ?: "Not Set"}"
            emailText.text = "Email: ${user.email ?: "No Email"}"
            usernameText.text = "Username: ${user.uid.take(8)}"
        } else {
            nameText.text = "Not logged in"
            emailText.text = ""
            usernameText.text = ""
        }
    }
}

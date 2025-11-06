package com.example.streamchat.ui

import android.app.Activity
import android.content.Intent
import com.example.streamchat.R

fun Activity.startActivityWithSlide(intent: Intent) {
    startActivity(intent)
    try {
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    } catch (_: Exception) { }
}

fun Activity.finishWithSlide() {
    finish()
    try {
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    } catch (_: Exception) { }
}

package com.example.hideandseek

data class ChatClass(
    var userName: String? = null,
    var message: String? = null,
    var gameSession: String? = null,
    var seeker: Boolean? = true
)
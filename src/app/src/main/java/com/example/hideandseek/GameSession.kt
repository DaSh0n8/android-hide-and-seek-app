package com.example.hideandseek

data class GameSession(
    val sessionId: String,
    val gameStatus: String,
    val players: List<Player>,
    val gameLength: Int
)
package com.example.hideandseek

data class GameSessionClass(
    val sessionId: String,
    val gameStatus: String,
    val players: List<PlayerClass>,
    val gameLength: Int
)
package com.example.hideandseek

class GameSessionClass() {
    var sessionId: String = ""
    var gameStatus: String = ""
    var players: List<PlayerClass> = listOf()
    var gameLength: Int = 0
    var seekersNumber: Int = 0
    var hidersNumber: Int = 0
    var radius: Int = 100

    constructor(
        sessionId: String,
        gameStatus: String,
        players: List<PlayerClass>,
        gameLength: Int,
        seekersNumber: Int,
        hidersNumber: Int,
        radius: Int
    ) : this() {
        this.sessionId = sessionId
        this.gameStatus = gameStatus
        this.players = players
        this.gameLength = gameLength
        this.seekersNumber = seekersNumber
        this.hidersNumber = hidersNumber
        this.radius = radius
    }
}
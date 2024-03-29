package com.example.hideandseek

class GameSessionClass() {
    var sessionId: String = ""
    var gameStatus: String = ""
    var players: List<PlayerClass> = listOf()
    var gameLength: Int = 0
    var hidingTime: Int = 0
    var updateInterval: Int = 0
    var radius: Int = 100
    var geofenceLat: Double = 0.0
    var geofenceLon: Double = 0.0

    constructor(
        sessionId: String,
        gameStatus: String,
        players: List<PlayerClass>,
        gameLength: Int,
        hidingTime: Int,
        updateInterval: Int,
        radius: Int,
        geofenceLat: Double,
        geofenceLon: Double
    ) : this() {
        this.sessionId = sessionId
        this.gameStatus = gameStatus
        this.players = players
        this.gameLength = gameLength
        this.hidingTime = hidingTime
        this.updateInterval = updateInterval
        this.radius = radius
        this.geofenceLat = geofenceLat
        this.geofenceLon = geofenceLon
    }
}
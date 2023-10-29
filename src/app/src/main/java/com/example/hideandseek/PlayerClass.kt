package com.example.hideandseek

class PlayerClass() {
    var userName: String = ""
    var seeker: Boolean = false
    var latitude: Double? = 0.0
    var longitude: Double? = 0.0
    var eliminated: Boolean = false
    var host: Boolean = false
    var playerCode: String = ""
    var lastUpdated: String? = null
    var playerStatus: String? = ""

    constructor(
        userName: String,
        seeker: Boolean,
        latitude: Double?,
        longitude: Double?,
        eliminated: Boolean,
        host: Boolean,
        playerCode: String,
        lastUpdated: String?,
        playerStatus: String
    ) : this() {
        this.userName = userName
        this.seeker = seeker
        this.latitude = latitude
        this.longitude = longitude
        this.eliminated = eliminated
        this.host = host
        this.playerCode = playerCode
        this.lastUpdated = lastUpdated
        this.playerStatus = playerStatus
    }
}


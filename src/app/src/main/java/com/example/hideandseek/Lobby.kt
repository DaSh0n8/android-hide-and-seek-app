package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class Lobby : AppCompatActivity() {
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var storageDb: FirebaseStorage
    private lateinit var lobbyListener: ValueEventListener

    private var seeker: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lobby)

        val receivedUserIcon = intent.getByteArrayExtra("userIcon")
        val receivedUsername = intent.getStringExtra("username_key")!!
        val receivedLobbyCode = intent.getStringExtra("lobby_key")!!
        seeker = intent.getBooleanExtra("isSeeker", false)

        val host = intent.getBooleanExtra("host", false)
        val lobbyHeader = findViewById<TextView>(R.id.lobbyHeader)
        val lobbyCode = "Lobby #$receivedLobbyCode"
        lobbyHeader.text = lobbyCode

        val hidersListView = findViewById<ListView>(R.id.hiderListView)
        val seekersListView = findViewById<ListView>(R.id.seekerListView)

        // get firebase real time db and storage db
        val application = application as HideAndSeek
        realtimeDb = application.getRealtimeDb()
        storageDb = application.getStorageDb()

        // upload user icon if available
        if (receivedUserIcon != null) {
            uploadIcon(receivedUserIcon, receivedLobbyCode, receivedUsername)
        }

        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(receivedLobbyCode)
        val seekersList = mutableListOf<String>()
        val hidersList = mutableListOf<String>()

        lobbyListener = query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                seekersList.clear()
                hidersList.clear()

                for (sessionSnapshot in dataSnapshot.children) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    // check if host has started or ended the game
                    when (gameSession!!.gameStatus) {
                        "started" -> startGameIntent(receivedLobbyCode, receivedUsername, gameSession)
                        "ended"   -> returnHomeIntent(receivedLobbyCode, host, false)
                    }

                    val players = sessionSnapshot.child("players").children
                    // update player list
                    for (playerSnapshot in players) {
                        val playerName = playerSnapshot.child("userName").value.toString()
                        val isSeeker = playerSnapshot.child("seeker").getValue(Boolean::class.java) ?: false

                        if (isSeeker) {
                            seekersList.add(playerName)
                        } else {
                            hidersList.add(playerName)
                        }
                    }
                }

                val seekersAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, seekersList)
                val hidersAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, hidersList)

                seekersListView.adapter = seekersAdapter
                hidersListView.adapter = hidersAdapter
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        val switchTeamButton: Button = findViewById(R.id.switchTeamButton)
        switchTeamButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                switchTeamClicked(receivedUsername, receivedLobbyCode)
            }
        }

        val leaveLobbyButton: Button = findViewById(R.id.leaveLobbyButton)
        leaveLobbyButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                leaveLobby(receivedLobbyCode, receivedUsername)
            }
        }

        val startGameButton: Button = findViewById(R.id.startGameButton)
        startGameButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                startButtonClicked(receivedLobbyCode)
            }
        }

        val updateGameSettings: FrameLayout = findViewById(R.id.settingsPlaceholder)
        updateGameSettings.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                val intent = Intent(this@Lobby, LobbySettings::class.java)
                intent.putExtra("lobby_code_key", receivedLobbyCode)
                intent.putExtra("username_key", receivedUsername)
                intent.putExtra("host", host)
                startActivity(intent)
            }
        }

        // hide the start game button for non host users
        if (!host) {
            startGameButton.visibility = GONE
            val waitHost: TextView = findViewById(R.id.waitHost)
            waitHost.visibility = VISIBLE
            updateGameSettings.visibility = GONE
        }

    }

    private fun switchTeamClicked(username: String?, lobbyCode: String?){
        if (username == null || lobbyCode == null){
            return
        }
        val gameSessionRef = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        gameSessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (sessionSnapshot in dataSnapshot.children) {
                    val players = sessionSnapshot.child("players").children
                    for (playerSnapshot in players) {
                        val playerName = playerSnapshot.child("userName").value.toString()
                        if (playerName == username) {
                            // Get the current seeker status
                            val isSeeker = playerSnapshot.child("seeker").getValue(Boolean::class.java) ?: false

                            seeker = if (isSeeker) {
                                playerSnapshot.ref.child("seeker").setValue(false)
                                false
                            } else{
                                playerSnapshot.ref.child("seeker").setValue(true)
                                true
                            }

                            return
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error updating team", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun uploadIcon(userIcon: ByteArray?, lobbyCode: String?, username: String?) {
        // get storage path
        var storageRef = storageDb.reference
        val pathRef = storageRef.child("$lobbyCode/$username.jpg")

        // Upload user icon
        pathRef.putBytes(userIcon!!).addOnFailureListener{
            Log.e("ICON FAILURE", it.toString())
        }
    }

    private fun leaveLobby(lobbyCode: String?, username: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        val playerIsHost = gameSession.players.find { it.userName == username }?.host == true

                        if (playerIsHost) {
                            // Update the local GameSession object
                            gameSession?.gameStatus = "ended"
                            gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
                                Toast.makeText(this@Lobby, "Game session ended", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.removeIf { it.userName == username}

                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
                                Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                        }

                        returnHomeIntent(lobbyCode, playerIsHost, true)

                    } else {
                        Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun startButtonClicked(lobbyCode: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    // validate the eligibility to start a game
                    if (validateGame(gameSession!!.players)) {
                        // Update the local GameSession object
                        gameSession?.gameStatus = "started"

                        // Save the updated GameSession back to Firebase
                        gameSessionSnapshot.ref.setValue(gameSession)
                            .addOnFailureListener {
                                // If there is an error updating Firebase, show an error message
                                Toast.makeText(
                                    this@Lobby,
                                    "Error starting the game",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        Toast.makeText(this@Lobby, "At least 1 seeker and 1 hider required!", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(this@Lobby, "Game session not found", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun startGameIntent(lobbyCode: String?, username: String?, gameSession: GameSessionClass?) {
        gameSession?.let {
            val intent = Intent(this@Lobby, GamePlay::class.java)
            intent.putExtra("lobbyCode", lobbyCode)
            intent.putExtra("username", username)
            intent.putExtra("gameLength", it.gameLength)
            intent.putExtra("hidingTime", it.hidingTime)
            intent.putExtra("updateInterval", it.updateInterval)
            intent.putExtra("radius", it.radius)

            // retrieve player info
            retrievePlayerInfo(lobbyCode, username) { player ->
                if (player != null) {
                    Log.e("PlayerInfo", player.playerCode.toString())
                    intent.putExtra("playerCode", player.playerCode)
                    intent.putExtra("isSeeker", player.seeker)

                    // start game
                    startActivity(intent)
                    removeLobbyListener(lobbyCode!!)
                    finish()
                } else {
                    Log.e("PlayerInfo", "Player not found or an error occurred.")
                }
            }

        } ?: Toast.makeText(
            this@Lobby,
            "Error retrieving session data",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Validate the minimum 1 player in each hider and seeker before starting the game
     */
    private fun validateGame(playerList: List<PlayerClass>): Boolean {
        var numHiders = 0
        var numSeekers = 0

        playerList.forEach {
            if (it.seeker) {
                numSeekers += 1
            } else {
                numHiders += 1
            }
        }

        return (numHiders > 0 && numSeekers > 0)
    }

    private fun removeLobbyListener(lobbyCode: String) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.removeEventListener(lobbyListener)
    }

    /**
     * Retrieve player and game
     */
    private fun retrievePlayerInfo(lobbyCode: String?, username: String?, callback: (PlayerClass?) -> Unit) {
        // query the db to get the user's session
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.get().addOnSuccessListener { snapshot ->
            // get the game session
            val gameSessionSnapshot = snapshot.children.first()
            val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

            if (gameSession != null) {
                val players = gameSession.players.toMutableList()

                for (p in players) {
                    if (p.userName == username) {
                        // Invoke the callback with the player object
                        callback(p)
                        return@addOnSuccessListener
                    }
                }
            }

            // If player is not found, invoke the callback with null
            callback(null)
        }.addOnFailureListener { exception ->
            Log.e("Error Player", exception.toString())
            // Invoke the callback with null in case of failure
            callback(null)
        }
    }

    private fun returnHomeIntent(lobbyCode: String?, host: Boolean?, voluntary: Boolean) {
        val intent = Intent(this@Lobby, HomeScreen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        if (!host!! && !voluntary) {
            Toast.makeText(this@Lobby, "Host has left", Toast.LENGTH_SHORT).show()
        } else {
            intent.putExtra("lobby_key", lobbyCode)
        }
        removeLobbyListener(lobbyCode!!)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        val leaveLobbyButton: Button = findViewById(R.id.leaveLobbyButton)
        leaveLobbyButton.performClick()
        super.onBackPressed()

    }

}
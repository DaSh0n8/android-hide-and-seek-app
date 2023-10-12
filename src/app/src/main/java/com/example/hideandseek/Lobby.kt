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

        val receivedUsername: String? = intent.getStringExtra("username_key")
        var receivedUserIcon: ByteArray? = intent.getByteArrayExtra("userIcon")
        val receivedLobbyCode: String? = intent.getStringExtra("lobby_key")
        val receivedPlayerCode: String? = intent.getStringExtra("playerCode")
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
        } else {
            receivedUserIcon = retrieveIcon(receivedLobbyCode, receivedUsername)
        }

        val updateGameSettings: FrameLayout = findViewById(R.id.settingsPlaceholder)
        updateGameSettings.setOnClickListener {
            val intent = Intent(this@Lobby, LobbySettings::class.java)
            intent.putExtra("lobby_code_key", receivedLobbyCode)
            intent.putExtra("username_key", receivedUsername)
            startActivity(intent)
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
                    // check if host has started the game
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                    if (gameSession!!.gameStatus == "started") {
                        startGameIntent(receivedLobbyCode, receivedUsername, gameSession, receivedPlayerCode)
                    }

                    // update player list
                    val players = sessionSnapshot.child("players").children
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
            switchTeamClicked(receivedUsername, receivedLobbyCode)
        }

        val leaveLobbyButton: Button = findViewById(R.id.leaveLobbyButton)
        leaveLobbyButton.setOnClickListener {
            removePlayer(receivedLobbyCode,receivedUsername)
        }

        val startGameButton: Button = findViewById(R.id.startGameButton)
        startGameButton.setOnClickListener {
            startButtonClicked(receivedLobbyCode,receivedUsername, receivedPlayerCode)
        }
        // hide the start game button for non host users
        if (!host) {
            startGameButton.visibility = GONE
            val waitHost: TextView = findViewById(R.id.waitHost)
            waitHost.visibility = VISIBLE
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

                            if (isSeeker) {
                                playerSnapshot.ref.child("seeker").setValue(false)
                                seeker = false
                            } else{
                                playerSnapshot.ref.child("seeker").setValue(true)
                                seeker = true
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

    private fun removePlayer(lobbyCode: String?, username: String?) {
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
                            gameSessionSnapshot.ref.removeValue().addOnSuccessListener {
                                Toast.makeText(this@Lobby, "Game session ended", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@Lobby, HomeScreen::class.java)
                                intent.putExtra("lobby_key", lobbyCode)
                                startActivity(intent)
                            }.addOnFailureListener {
                                Toast.makeText(this@Lobby, "Error deleting session", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.removeIf { it.userName == username}

                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
                                Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                            val intent = Intent(this@Lobby, HomeScreen::class.java)
                            intent.putExtra("lobby_key", lobbyCode)
                            startActivity(intent)
                        }

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

    private fun startButtonClicked(lobbyCode: String?, username: String?, playerCode: String?) {
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
                            .addOnSuccessListener {
                                // If the update is successful, start the game
                                startGameIntent(lobbyCode, username, gameSession, playerCode)
                            }
                            .addOnFailureListener {
                                // If there is an error updating Firebase, show an error message
                                Toast.makeText(
                                    this@Lobby,
                                    "Error updating game status in Firebase",
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

    private fun startGameIntent(lobbyCode: String?, username: String?, gameSession: GameSessionClass?, playerCode: String?) {
        gameSession?.let {
            val intent = Intent(this@Lobby, GamePlay::class.java)
            intent.putExtra("lobbyCode", lobbyCode)
            intent.putExtra("username", username)
            intent.putExtra("gameLength", it.gameLength)
            intent.putExtra("hidingTime", it.hidingTime)
            intent.putExtra("updateInterval", it.updateInterval)
            intent.putExtra("radius", it.radius)
            intent.putExtra("isSeeker", seeker)
            intent.putExtra("playerCode", playerCode)
            startActivity(intent)
            removeLobbyListener(lobbyCode!!)
            finish()
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
     * Retrieve player icon if available
     */
    private fun retrieveIcon(lobbyCode: String?, username: String?): ByteArray? {
        // get storage path
        var storageRef = storageDb.reference
        val pathRef = storageRef.child("$lobbyCode/$username.jpg")
        var userIcon: ByteArray? = null

        pathRef.getBytes(1000000)
            .addOnSuccessListener {
                userIcon = it
        }
        return  userIcon
    }


}
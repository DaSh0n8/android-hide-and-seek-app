package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class Lobby : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lobby)

        val receivedUsername: String? = intent.getStringExtra("username_key")
        val receivedUserIcon: ByteArray? = intent.getByteArrayExtra("userIcon")
        val receivedLobbyCode: String? = intent.getStringExtra("lobby_key")
        val lobbyHeader = findViewById<TextView>(R.id.lobbyHeader)
        val lobbyCode = "Lobby #$receivedLobbyCode"
        lobbyHeader.text = lobbyCode


        val hidersListView = findViewById<ListView>(R.id.hiderListView)
        val seekersListView = findViewById<ListView>(R.id.seekerListView)

        FirebaseApp.initializeApp(this)
        // YOUR OWN DATABASE URL
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        // upload user icon if available
        if (receivedUserIcon != null) {
            uploadIcon(receivedUserIcon, receivedLobbyCode, receivedUsername)
        }

        val updateGameSettings: FrameLayout = findViewById(R.id.settingsPlaceholder)
        updateGameSettings.setOnClickListener {
            val intent = Intent(this@Lobby, LobbySettings::class.java)
            intent.putExtra("lobby_code_key", receivedLobbyCode)
            startActivity(intent)
        }


        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(receivedLobbyCode)
        val seekersList = mutableListOf<String>()
        val hidersList = mutableListOf<String>()

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                seekersList.clear()
                hidersList.clear()

                for (sessionSnapshot in dataSnapshot.children) {
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
            startButtonClicked(receivedLobbyCode,receivedUsername)
        }


    }

    private fun switchTeamClicked(username: String?, lobbyCode: String?){
        if (username == null || lobbyCode == null){
            return
        }
        val gameSessionRef = database.getReference("gameSessions")
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
                            } else{
                                playerSnapshot.ref.child("seeker").setValue(true)
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
        var storage = FirebaseStorage.getInstance("gs://hide-and-seek-4983f.appspot.com")
        var storageRef = storage.reference
        val pathRef = storageRef.child("$lobbyCode/$username.jpg")

        // Upload user icon
        pathRef.putBytes(userIcon!!)
    }

    private fun removePlayer(lobbyCode: String?, username: String?) {
        val query = database.getReference("gameSessions")
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

    private fun startButtonClicked(lobbyCode: String?, username: String?){
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    gameSession?.let {
                        val intent = Intent(this@Lobby, GamePlay::class.java)
                        intent.putExtra("lobbyCode", lobbyCode)
                        intent.putExtra("username", username)
                        intent.putExtra("gameLength", it.gameLength)
                        intent.putExtra("hidingTime", it.hidingTime)
                        intent.putExtra("updateInterval", it.updateInterval)
                        intent.putExtra("radius",it.radius)
                        startActivity(intent)
                    } ?: Toast.makeText(this@Lobby, "Error retrieving session data", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@Lobby, "Game session not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

}
package com.example.hideandseek

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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
import java.time.Duration
import java.time.LocalTime

class Lobby : AppCompatActivity() {
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var storageDb: FirebaseStorage
    private lateinit var lobbyListener: ValueEventListener
    private lateinit var connectTimer: CountDownTimer

    private lateinit var seekersAdapter: PlayersAdapter
    private lateinit var hidersAdapter: PlayersAdapter
    private lateinit var seekersNameAdapter: ArrayAdapter<String>
    private lateinit var hidersNameAdapter: ArrayAdapter<String>
    private var seeker: Boolean = false
    private var currentLobbyCode: String? = null
    private var currentUserName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lobby)

        val receivedUserIcon = intent.getByteArrayExtra("userIcon")
        val receivedUsername = intent.getStringExtra("username_key")!!
        val receivedLobbyCode = intent.getStringExtra("lobby_key")!!
        currentLobbyCode = receivedLobbyCode
        currentUserName = receivedUsername
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

        // update user's connectivity
//        var tickCounter = 0
//        val interval = 10
//        connectTimer = object: CountDownTimer(Long.MAX_VALUE, 1000) {
//            override fun onTick(millisUntilFinished: Long) {
//                if (tickCounter == interval) {
//                    if (NetworkUtils.checkForInternet(this@Lobby)){
//                        acknowledgeOnline(receivedLobbyCode, receivedUsername)
//                        checkPlayerActivity(receivedLobbyCode)
//                    } else {
//                        val intent = Intent(this@Lobby, HomeScreen::class.java)
//                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
//                        removeLobbyListener(lobbyCode)
//                        Toast.makeText(this@Lobby, "You have been disconnected from the lobby", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                        finish()
//                    }
//
//                    tickCounter = 0
//                }
//                tickCounter++
//            }
//            override fun onFinish() {
//
//            }
//        }.start()


        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(receivedLobbyCode)
        val seekersList = mutableListOf<PlayerClass>()
        val hidersList = mutableListOf<PlayerClass>()

        seekersAdapter = PlayersAdapter(this@Lobby, seekersList, receivedLobbyCode, realtimeDb)
        hidersAdapter = PlayersAdapter(this@Lobby, hidersList, receivedLobbyCode, realtimeDb)

        val seekersNames = mutableListOf<String>()
        val hidersNames = mutableListOf<String>()


        seekersNameAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, seekersNames)
        hidersNameAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, hidersNames)


        if (host) {
            seekersListView.adapter = seekersAdapter
            hidersListView.adapter = hidersAdapter
        } else {
            seekersListView.adapter = seekersNameAdapter
            hidersListView.adapter = hidersNameAdapter
        }

        val chatButton: Button = findViewById(R.id.chatButton)
        chatButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                showChatOverlay(receivedUsername, receivedLobbyCode)
            }
        }

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

        lobbyListener = query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                seekersList.clear()
                hidersList.clear()

                var currentUserIsHost = false

                for (sessionSnapshot in dataSnapshot.children) {
                    seekersAdapter.updateData(seekersList)
                    hidersAdapter.updateData(hidersList)
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
                        val player = playerSnapshot.getValue(PlayerClass::class.java) ?: return
                        if (player.seeker) {
                            seekersList.add(player)
                        } else {
                            hidersList.add(player)
                        }
                        if (player.userName == receivedUsername && player.host) {
                            currentUserIsHost = true
                        }
                    }
                    val playerStillInSession = (seekersList + hidersList).any { it.userName == receivedUsername }

                    if (!playerStillInSession) {
                        returnHomeIntent(receivedLobbyCode, currentUserIsHost, false)
                        return
                    }

                    if (currentUserIsHost) {
                        seekersAdapter.updateData(seekersList)
                        hidersAdapter.updateData(hidersList)
                    } else {
                        seekersNameAdapter.clear()
                        seekersNameAdapter.addAll(seekersList.map { it.userName })
                        seekersNameAdapter.notifyDataSetChanged()

                        hidersNameAdapter.clear()
                        hidersNameAdapter.addAll(hidersList.map { it.userName })
                        hidersNameAdapter.notifyDataSetChanged()
                    }
                }
                updateUIBasedOnHostStatus(currentUserIsHost)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun showChatOverlay(username: String?, lobbyCode: String?) {
        val fragmentManager = supportFragmentManager
        val newFragment = ChatOverlay.newInstance(username?: "Anonymous", lobbyCode?: "Unknown", seeker)
        newFragment.show(fragmentManager, "chat_overlay")
    }

    private fun updateUIBasedOnHostStatus(isHost: Boolean) {
        val startGameButton: Button = findViewById(R.id.startGameButton)
        val waitHost: TextView = findViewById(R.id.waitHost)
        val updateGameSettings: FrameLayout = findViewById(R.id.settingsPlaceholder)
        val hidersListView = findViewById<ListView>(R.id.hiderListView)
        val seekersListView = findViewById<ListView>(R.id.seekerListView)

        if (isHost) {
            startGameButton.visibility = VISIBLE
            waitHost.visibility = GONE
            updateGameSettings.visibility = VISIBLE

            seekersListView.adapter = seekersAdapter
            hidersListView.adapter = hidersAdapter
        } else {
            startGameButton.visibility = GONE
            waitHost.visibility = VISIBLE
            updateGameSettings.visibility = GONE

            seekersListView.adapter = seekersNameAdapter
            hidersListView.adapter = hidersNameAdapter
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
        connectTimer.cancel()
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

                    for (player in gameSession!!.players) {
                        if (player.playerStatus != "In Lobby") {
                            Toast.makeText(this@Lobby, "Not all players are in the lobby", Toast.LENGTH_SHORT).show()
                            return
                        }
                    }

                    // validate the eligibility to start a game
                    if (validateGame(gameSession!!.players)) {
                        // Update the local GameSession object
                        gameSession?.gameStatus = "started"

                        for (player in gameSession.players) {
                            player.playerStatus = "In game"
                        }

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
        connectTimer.cancel()
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
            Toast.makeText(this@Lobby, "You have been disconnected from the lobby", Toast.LENGTH_SHORT).show()
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

    /**
     * Function to allow user to update their last online time
     */
    private fun acknowledgeOnline(lobbyCode: String?, username: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()

                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = realtimeDb.getReference("gameSessions").child(gameSessionSnapshot.key!!)

                if (gameSession != null) {
                    // update user's last update time
                    val players = gameSession.players.toMutableList()
                    for ((index, p) in players.withIndex()) {
                        if (p.userName == username) {
                            val currTime = LocalTime.now().toString()
                            ref.child("players").child(index.toString()).child("lastUpdated").setValue(currTime)
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    private fun checkPlayerActivity(lobbyCode: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                    if (gameSession != null) {
                        val currentTime = LocalTime.now()
                        for (player in gameSession.players) {
                            val lastUpdatedTime = LocalTime.parse(player.lastUpdated)
                            val duration = Duration.between(lastUpdatedTime, currentTime)
                            if (duration.seconds > 30) {
                                kickPlayer(lobbyCode, player.userName)
                                Log.e("checkPlayerActivity", "Kicking the player ${player.userName}")
                            }
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    private fun kickPlayer(lobbyCode: String?, playerToKick: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        val playerIsHost = gameSession.players.any { it.userName == playerToKick && it.host }

                        if (playerIsHost) {
                            gameSessionSnapshot.ref.removeValue().addOnSuccessListener {

                                Toast.makeText(this@Lobby, "The game session has ended as the host left", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@Lobby, HomeScreen::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK

                                removeLobbyListener(lobbyCode!!)
                                startActivity(intent)
                                finish()

                            }.addOnFailureListener {
                                Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.removeIf { it.userName == playerToKick }

                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnSuccessListener {
                                Toast.makeText(this@Lobby, "$playerToKick was removed due to inactivity", Toast.LENGTH_SHORT).show()
                            }.addOnFailureListener {
                                Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onStop() {
        super.onStop()
        connectTimer.cancel()
    }
    override fun onStart() {
        super.onStart()
        var tickCounter = 0
        val interval = 10
        connectTimer = object: CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (tickCounter == interval) {
                    if (NetworkUtils.checkForInternet(this@Lobby)){
                        acknowledgeOnline(currentLobbyCode, currentUserName)
                        checkPlayerActivity(currentLobbyCode)
                    } else {
                        val intent = Intent(this@Lobby, HomeScreen::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        currentLobbyCode?.let { removeLobbyListener(it) }
                        Toast.makeText(this@Lobby, "You have been disconnected from the lobby", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                        finish()
                    }

                    tickCounter = 0
                }
                tickCounter++
            }
            override fun onFinish() {

            }
        }.start()
    }

//    override fun onStop() {
//        super.onStop()
//        val query = realtimeDb.getReference("gameSessions")
//            .orderByChild("sessionId")
//            .equalTo(currentLobbyCode)
//
//        query.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(dataSnapshot: DataSnapshot) {
//                if (dataSnapshot.exists()) {
//                    val gameSessionSnapshot = dataSnapshot.children.first()
//                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
//
//                    if (gameSession != null) {
//                        val playerIsHost = gameSession.players.find { it.userName == currentUserName }?.host == true
//
//                        if (playerIsHost) {
//                            // Update the local GameSession object
//                            gameSession?.gameStatus = "ended"
//                            gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
//                                Toast.makeText(this@Lobby, "Game session ended", Toast.LENGTH_SHORT).show()
//                            }
//                        } else {
//                            val updatedPlayers = gameSession.players.toMutableList()
//                            updatedPlayers.removeIf { it.userName == currentUserName}
//
//                            gameSession.players = updatedPlayers
//                            gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
//                                Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//
//                        returnHomeIntent(currentLobbyCode, playerIsHost, true)
//
//                    } else {
//                        Toast.makeText(this@Lobby, "Unexpected Error", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//
//            override fun onCancelled(databaseError: DatabaseError) {
//                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
//                    .show()
//            }
//        })
//
//    }

}
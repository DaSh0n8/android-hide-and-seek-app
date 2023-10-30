package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalTime

class GameOver : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase

    private val youWon = "Congrats, You Won!!!"
    private val youLost = "Sorry, You Lost!!!"
    private lateinit var connectTimer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_over)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        val username: String? = intent.getStringExtra("username")
        val lobbyCode: String? = intent.getStringExtra("lobbyCode")
        val host: Boolean? = intent.getBooleanExtra("host", false)
        val isSeeker: Boolean? = intent.getBooleanExtra("isSeeker", false)
        val seekerWon = intent.getBooleanExtra("seekerWonGame", false)

        confirmConnectivity(lobbyCode, username)

        // set the result views
        val resultText: TextView = findViewById(R.id.resultText)
        val resultImg: ImageView = findViewById(R.id.resultView)
        if (seekerWon) {
            resultImg.setImageResource(R.drawable.seekers_win)
            if (isSeeker!!) {
                resultText.text = youWon
            } else {
                resultText.text = youLost
            }
        } else {
            resultImg.setImageResource(R.drawable.hiders_win)
            if (isSeeker!!) {
                resultText.text = youLost
            } else {
                resultText.text = youWon
            }
        }

        // clean db and return to home page
        val backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (host!!) {
                    endGameSession(lobbyCode)
                } else {
                    removePlayer(lobbyCode, username)
                }

                // return to homepage
                val intent = Intent(this@GameOver, HomeScreen::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                connectTimer.cancel()
                finish()
            }
        }

        // back to lobby
        var backToLobbyBtn: Button = findViewById(R.id.btnPlayAgain)
        backToLobbyBtn.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                returnLobby(username, lobbyCode, host)
            }
        }
    }

    /**
     * End the game session and return home
     */
    private fun endGameSession(lobbyCode: String?) {
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        // Update the GameSession object
                        gameSession?.gameStatus = "ended"

                        for (player in gameSession.players) {
                            player.playerStatus = "End Game Screen"
                        }
                        gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
                            Toast.makeText(this@GameOver, "Game session ended", Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        Toast.makeText(this@GameOver, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@GameOver, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    /**
     * Remove player from game session in database and return home
     */
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
                        val updatedPlayers = gameSession.players.toMutableList()
                        updatedPlayers.removeIf { it.userName == username}

                        gameSession.players = updatedPlayers
                        gameSessionSnapshot.ref.setValue(gameSession).addOnFailureListener {
                            Toast.makeText(this@GameOver, "Unexpected Error", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@GameOver, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@GameOver, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    /**
     * Return to lobby
     */
    private fun returnLobby(username: String?, lobbyCode: String?, host: Boolean?) {
        var hostExist = false
        val intent = Intent(this@GameOver, Lobby::class.java)
        intent.putExtra("username_key", username)
        intent.putExtra("lobby_key", lobbyCode)
        intent.putExtra("host", host)

        // reset the players'status
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = database.getReference("gameSessions").child(gameSessionSnapshot.key!!)

                if (gameSession != null) {
                    val players = gameSession.players.toMutableList()
                    val codes = listOf<String>().toMutableList()
                    for ((index, p) in players.withIndex()) {
                        if (p.host) {
                            hostExist = true
                        }

                        if (p.userName == username) {
                            p.eliminated = false
                            p.playerStatus = "In Lobby"

                            // reset player code
                            var randomNum = ((0..9999).random())
                            var playerCode = String.format("%04d",randomNum)
                            while(codes.contains(playerCode)){
                                randomNum = ((0..9999).random())
                                playerCode = String.format("%04d",randomNum)
                            }
                            p.playerCode = playerCode
                            ref.child("players").child(index.toString()).setValue(p)

                        }
                    }

                    // Update the local GameSession object
                    gameSession.players = players

                    // Save the updated GameSession back to Firebase
                    gameSessionSnapshot.ref.setValue(gameSession)
                        .addOnFailureListener {
                            Toast.makeText(
                                this@GameOver,
                                "Error updating game status in Firebase",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
        if (hostExist) {
            startActivity(intent)
            finish()
        } else {
            hostLeftDialog()
        }
    }

    override fun onBackPressed() {
        var backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.performClick()
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        connectTimer.cancel()
    }

    /**
     * Function to allow user to update their last online time
     */
    private fun acknowledgeOnline(lobbyCode: String?, username: String?) {
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = database.getReference("gameSessions").child(gameSessionSnapshot.key!!)

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

    /**
     * Function to ensure user connectivity
     */
    private fun confirmConnectivity(lobbyCode: String?, username: String?) {
        var tickCounter = 0
        val checkpoint = 5
        connectTimer = object: CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (tickCounter == checkpoint) {
                    acknowledgeOnline(lobbyCode, username)
                    tickCounter = 0
                }
                tickCounter++
            }
            override fun onFinish() {

            }
        }.start()
    }

    private fun hostLeftDialog(){
        val builder = AlertDialog.Builder(this)
        with(builder)
        {
            setTitle("Sorry...")
            setMessage("Host has left the game")
            setPositiveButton("OK"){ _, _ ->
                val backToHomeBtn: Button = findViewById(R.id.btnHome)
                backToHomeBtn.performClick()
            }
            show()
        }
    }
}
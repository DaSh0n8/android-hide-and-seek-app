package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GameOver : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase

    private val youWon = "Congrats, You Won!!!"
    private val youLost = "Sorry, You Lost!!!"

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
        var backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (host!!) {
                    endGame(lobbyCode)
                } else {
                    removePlayer(lobbyCode, username)
                }

                // return to homepage
                val intent = Intent(this@GameOver, HomeScreen::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
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
    private fun endGame(lobbyCode: String?) {
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
        val intent = Intent(this@GameOver, Lobby::class.java)
        intent.putExtra("username_key", username)
        intent.putExtra("lobby_key", lobbyCode)
        intent.putExtra("host", host)
        startActivity(intent)
    }

    override fun onBackPressed() {
        var backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.performClick()
        super.onBackPressed()
    }
}
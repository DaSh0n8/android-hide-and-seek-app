package com.example.hideandseek

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GameOver : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_over)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        val username: String? = intent.getStringExtra("username")
        val sessionId: String? = intent.getStringExtra("sessionId")
        val lobbyCode: String? = intent.getStringExtra("lobbyCode")
        val playerCode: String? = intent.getStringExtra("playerCode")
        val host: Boolean? = intent.getBooleanExtra("host", false)

        // set the result text
        var resultText: TextView = findViewById(R.id.resultText)
        resultText.text = intent.getStringExtra("result")

        // clean db and return to home page
        var backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.setOnClickListener {
            if (host!!) {
                removeGame(username, sessionId, host)
            } else {
                removePlayer(lobbyCode, username)
            }
            // return to homepage
            val intent = Intent(this@GameOver, HomeScreen::class.java)
            startActivity(intent)
            finish()
        }

        // back to lobby
        var backToLobbyBtn: Button = findViewById(R.id.btnPlayAgain)
        backToLobbyBtn.setOnClickListener { returnLobby(username, playerCode, lobbyCode, host) }
    }

    /**
     * Remove game session from database and return home
     */
    private fun removeGame(playerIndex: String?, sessionId: String?, host: Boolean?) {
        // delete game session
        val gameSessionRef = database.getReference("gameSessions").child(sessionId!!)
        gameSessionRef.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Game session removed successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to delete session: $exception")
            }
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
    private fun returnLobby(username: String?, playerCode: String?, lobbyCode: String?, host: Boolean?) {
        val intent = Intent(this@GameOver, Lobby::class.java)
        intent.putExtra("username_key", username)
        intent.putExtra("lobby_key", lobbyCode)
        intent.putExtra("host", host)
        startActivity(intent)
    }
}
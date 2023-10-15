package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class JoinGame : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.join_game)

        val joinGameButton: Button = findViewById(R.id.btnJoinGameLobby)
        joinGameButton.setOnClickListener {
            joinButtonClicked()
        }

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()
    }

    private fun joinButtonClicked() {
        val lobbyCodeInput: EditText = findViewById(R.id.lobbyCodeInput)
        val lobbyCode: String = lobbyCodeInput.text.toString().trim()

        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    // get the game session
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                    if (gameSession!!.gameStatus != "ongoing") {
                        Toast.makeText(this@JoinGame, "Invalid Lobby Code", Toast.LENGTH_SHORT).show()

                    } else {
                        val intent = Intent(this@JoinGame, UserSetting::class.java)
                        intent.putExtra("lobbyCode", lobbyCode)
                        startActivity(intent)
                    }

                } else {
                    Toast.makeText(this@JoinGame, "Invalid Lobby Code", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@JoinGame, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
}
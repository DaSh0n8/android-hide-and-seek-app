package com.example.hideandseek

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class JoinGame : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.join_game)

        val receivedUsername: String? = intent.getStringExtra("username_key")

        val joinGameButton: Button = findViewById(R.id.btnJoinGameLobby)
        joinGameButton.setOnClickListener {
            joinButtonClicked(receivedUsername)
        }
        FirebaseApp.initializeApp(this)
        // YOUR OWN DATABASE URL
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)
    }

    private fun joinButtonClicked(username: String?) {
        if (username == null){
            return
        }
        val lobbyCodeInput: EditText = findViewById(R.id.lobbyCodeInput)
        val lobbyCode: String = lobbyCodeInput.text.toString().trim()

        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        val newPlayer = PlayerClass(username, false, 0.0, 0.0, false, false)
                        val updatedPlayers = gameSession.players.toMutableList()
                        updatedPlayers.add(newPlayer)


                        gameSession.players = updatedPlayers
                        gameSessionSnapshot.ref.setValue(gameSession)
                    } else {
                        Toast.makeText(this@JoinGame, "Unexpected Error", Toast.LENGTH_SHORT).show()
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
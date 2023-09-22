package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.util.Random

class HomeScreen : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            createButtonClicked()
        }
        val joinGameButton: Button = findViewById(R.id.joinGameButton)
        joinGameButton.setOnClickListener {
            joinButtonClicked()
        }
        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)
    }

    private fun createButtonClicked(){
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        val intent = Intent(this, NewGameSettings::class.java)
        intent.putExtra("username_key", username)
        startActivity(intent)

//        val gameSessionRef = database.getReference("gameSessions").push()
//
//        val players = listOf(
//            PlayerClass(username, true, null, null, true, true)
//        )
//        val sessionId: Int = Random().nextInt(9999 - 1000 + 1) + 1000
//        val sessionIdString: String = sessionId.toString()
//        val newGameSession = GameSessionClass(sessionIdString, "ongoing", players, 300)
//        gameSessionRef.setValue(newGameSession)
    }

    private fun joinButtonClicked(){
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        val intent = Intent(this, JoinGame::class.java)
        intent.putExtra("username_key", username)
        startActivity(intent)
    }
}
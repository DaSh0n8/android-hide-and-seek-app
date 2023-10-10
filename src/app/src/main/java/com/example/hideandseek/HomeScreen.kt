package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class HomeScreen : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        val receivedLobbyCode: String? = intent.getStringExtra("lobby_key")
        if (receivedLobbyCode!=null){
            Toast.makeText(this@HomeScreen, "You have left lobby #$receivedLobbyCode", Toast.LENGTH_SHORT).show()
        }

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            val intent = Intent(this@HomeScreen, UserSetting::class.java)
            intent.putExtra("host", true)
            startActivity(intent)
        }
        val joinGameButton: Button = findViewById(R.id.joinGameButton)
        joinGameButton.setOnClickListener {
            val intent = Intent(this@HomeScreen, JoinGame::class.java)
            startActivity(intent)
        }
    }
}
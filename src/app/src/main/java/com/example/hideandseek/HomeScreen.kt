package com.example.hideandseek

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class HomeScreen : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        val intent = Intent(this@HomeScreen, UserSetting::class.java)
        intent.putExtra("origin", "home_screen")

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            intent.putExtra("host", true)
            startActivity(intent)
        }
        val joinGameButton: Button = findViewById(R.id.joinGameButton)
        joinGameButton.setOnClickListener {
            intent.putExtra("host", false)
            startActivity(intent)
        }
        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)
    }
}
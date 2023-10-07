package com.example.hideandseek

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class GameOver : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_over)

        val playerIndex: String? = intent.getStringExtra("playerIndex")
        val sessionId: String? = intent.getStringExtra("sessionId")
        val lobbyCode: String? = intent.getStringExtra("lobbyCode")
        val host: Boolean? = intent.getBooleanExtra("host", false)

        // set the result text
        var resultText: TextView = findViewById(R.id.resultText)
        resultText.text = intent.getStringExtra("result")

        // back to home page
        var backToHomeBtn: Button = findViewById(R.id.btnHome)
        backToHomeBtn.setOnClickListener { returnHome(playerIndex, sessionId, host) }
    }

    /**
     * Clean database and return home
     */
    private fun returnHome(playerIndex: String?, sessionId: String?, host: Boolean?) {
        // start the firebase
        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        // query the db to get the user's session
        if (host!!) {
            // delete game session
            val gameSessionRef = database.getReference("gameSessions").child(sessionId!!)
            gameSessionRef.removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "Game session removed successfully")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to delete session: $exception")
                }
        } else {
            // delete player from game session
            val playerReference =
                database.getReference("gameSessions").child(sessionId!!).child("players")
                    .child(playerIndex!!)
            playerReference.removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "Player removed successfully")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to remove player: $exception")
                }
        }

        // return to homepage
        val intent = Intent(this@GameOver, HomeScreen::class.java)
        startActivity(intent)
    }


}
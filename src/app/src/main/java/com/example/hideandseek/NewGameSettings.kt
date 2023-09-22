package com.example.hideandseek

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.util.Random

class NewGameSettings : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_game_settings)
        val createGameButton: Button = findViewById(R.id.btnStartGame)

        val receivedUsername: String? = intent.getStringExtra("username_key")

        createGameButton.setOnClickListener {
            createButtonClicked(receivedUsername)
        }

        FirebaseApp.initializeApp(this)
        // YOUR OWN DATABASE URL
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)
    }

    private fun createButtonClicked(username: String?){
        if (username == null){
            return
        }
        val hidersNumberInput: EditText = findViewById(R.id.editHiders)
        val hidersNumber: String = hidersNumberInput.text.toString().trim()

        val seekersNumberInput: EditText = findViewById(R.id.editSeekers)
        val seekersNumber: String = seekersNumberInput.text.toString().trim()



        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val gameTime: String = gameTimeInput.text.toString().trim()


        val gameSessionRef = database.getReference("gameSessions").push()

        val players = listOf(
            PlayerClass(username, true, null, null, true, true)
        )

        val sessionId: Int = Random().nextInt(9999 - 1000 + 1) + 1000
        val sessionIdString: String = sessionId.toString()
        val newGameSession = GameSessionClass(sessionIdString, "ongoing", players, 300, seekersNumber.toInt() ,hidersNumber.toInt())
        gameSessionRef.setValue(newGameSession)
    }
}
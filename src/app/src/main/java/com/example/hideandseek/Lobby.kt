package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Lobby : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lobby)


        val receivedLobbyCode: String? = intent.getStringExtra("lobby_key")
        val lobbyHeader = findViewById<TextView>(R.id.lobbyHeader)
        val lobbyCode = "Lobby #$receivedLobbyCode"
        lobbyHeader.text = lobbyCode

        val hidersListView = findViewById<ListView>(R.id.hiderListView)
        val seekersListView = findViewById<ListView>(R.id.seekerListView)

        FirebaseApp.initializeApp(this)
        // YOUR OWN DATABASE URL
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        val createGameButton: FrameLayout = findViewById(R.id.settingsPlaceholder)
        createGameButton.setOnClickListener {
            val intent = Intent(this@Lobby, LobbySettings::class.java)
            intent.putExtra("receivedLobbyCode", lobbyCode)
            startActivity(intent)
        }

        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(receivedLobbyCode)
        val seekersList = mutableListOf<String>()
        val hidersList = mutableListOf<String>()

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (sessionSnapshot in dataSnapshot.children) {
                    val players = sessionSnapshot.child("players").children
                    for (playerSnapshot in players) {
                        val playerName = playerSnapshot.child("userName").value.toString()
                        val isSeeker = playerSnapshot.child("seeker").getValue(Boolean::class.java) ?: false

                        if (isSeeker) {
                            seekersList.add(playerName)
                        } else {
                            hidersList.add(playerName)
                        }
                    }
                }

                val seekersAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, seekersList)
                val hidersAdapter = ArrayAdapter(this@Lobby, android.R.layout.simple_list_item_1, hidersList)

                seekersListView.adapter = seekersAdapter
                hidersListView.adapter = hidersAdapter
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Lobby, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })


    }

}
package com.example.hideandseek

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PlayersAdapter(private val context: Context, private var players: List<PlayerClass>, private val lobbyCode: String, private val realtimeDb: FirebaseDatabase) : BaseAdapter() {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getCount(): Int = players.size

    override fun getItem(position: Int): PlayerClass = players[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.player_list_layout, parent, false)
        val player = getItem(position)

        view.findViewById<TextView>(R.id.playerName).text = player.userName

        val btnTransferHost = view.findViewById<Button>(R.id.btnTransferHost)
        val btnKick = view.findViewById<Button>(R.id.btnKick)

        if (player.host) {
            btnTransferHost.visibility = View.GONE
            btnKick.visibility = View.GONE
        } else {
            btnTransferHost.visibility = View.VISIBLE
            btnKick.visibility = View.VISIBLE
        }

        btnTransferHost.setOnClickListener {
            transferHost(player.userName)
        }

        btnKick.setOnClickListener {
            kickPlayer(player.userName, context)
        }

        return view
    }
    fun updateData(newPlayers: List<PlayerClass>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    private fun transferHost(username: String?){
        if (username == null){
            return
        }
        val gameSessionRef = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        gameSessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (sessionSnapshot in dataSnapshot.children) {
                    val players = sessionSnapshot.child("players").children
                    for (playerSnapshot in players) {
                        val playerName = playerSnapshot.child("userName").value.toString()
                        if (playerName == username) {
                            playerSnapshot.ref.child("host").setValue(true)
                        } else{
                            playerSnapshot.ref.child("host").setValue(false)
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
            }
        })
    }

    private fun kickPlayer(playerToKick: String?, context: Context) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        val updatedPlayers = gameSession.players.toMutableList()
                        updatedPlayers.removeIf { it.userName == playerToKick }

                        gameSession.players = updatedPlayers
                        gameSessionSnapshot.ref.setValue(gameSession).addOnSuccessListener {
                            Toast.makeText(context, "$playerToKick was kicked out of the lobby", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            Toast.makeText(context, "Unexpected Error", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(context, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
}
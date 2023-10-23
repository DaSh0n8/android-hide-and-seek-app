package com.example.hideandseek

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class PlayersAdapter(private val context: Context, private val players: List<PlayerClass>) : BaseAdapter() {
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
            // handle transfer host logic
        }

        btnKick.setOnClickListener {
            // handle kick player logic
        }

        return view
    }
}
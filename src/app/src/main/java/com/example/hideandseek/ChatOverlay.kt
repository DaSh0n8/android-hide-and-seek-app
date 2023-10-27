package com.example.hideandseek

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ChatOverlay : DialogFragment() {
    companion object {
        const val ARG_USERNAME = "username"
        const val ARG_GAME_SESSION = "gameSession"

        fun newInstance(username: String, gameSession: String): ChatOverlay {
            val args = Bundle()
            args.putString(ARG_USERNAME, username)
            args.putString(ARG_GAME_SESSION, gameSession)
            val fragment = ChatOverlay()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<ChatClass>()
    private lateinit var databaseReference: DatabaseReference
    private lateinit var editText: EditText

    private var username: String? = null
    private var gameSession: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            username = it.getString(ARG_USERNAME)
            gameSession = it.getString(ARG_GAME_SESSION)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat_overlay, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseReference = FirebaseDatabase.getInstance().getReference("chat")

        editText = view.findViewById<EditText>(R.id.editText)
        val sendButton: Button = view.findViewById(R.id.sendButton)
        sendButton.setOnClickListener {
            val messageText = editText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                val chatMessage = ChatClass()
                chatMessage.userName = username ?: "Anonymous"
                chatMessage.message = messageText
                chatMessage.gameSession = gameSession ?: "Unknown"
                Log.d("ChatOverlay", "Chat Message: $chatMessage")
                databaseReference.push().setValue(chatMessage)
                editText.text?.clear()
            }
        }
        setupChat()
    }

    private fun setupChat() {
        messageAdapter = MessageAdapter(requireContext(), messages)
        val listView = view?.findViewById<ListView>(R.id.listView)
        listView?.adapter = messageAdapter

        val messagesQuery = databaseReference.orderByChild("gameSession").equalTo(gameSession)

        messagesQuery.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
                val chatMessage = dataSnapshot.getValue(ChatClass::class.java)
                if (chatMessage != null) {
                    messages.add(chatMessage)
                    messageAdapter.notifyDataSetChanged()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        })
    }

}


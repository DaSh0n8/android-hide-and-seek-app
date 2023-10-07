package com.example.hideandseek

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class UserSetting : AppCompatActivity() {
    /**
     *  1) Get Lobby Code DONE
     *  2) Recursive passing of host value (to selfie segmentation and back) DONE
     *  3) Check unique username
     *  4) Add user to the game session in db
     */
    private var host: Boolean = false
    private var lobbyCode: String? = null
    private var userIcon: Bitmap? = null
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_setting)

        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        // receive the info and user icon if any value sent from previous activity
        host = intent.getBooleanExtra("host", false)
        lobbyCode = intent.getStringExtra("lobbyCode")
        val byteArray = intent.getByteArrayExtra("userIcon")

        // show the selfie segmentation if available
        if (byteArray != null) {
            val byteArray = intent.getByteArrayExtra("userIcon")
            userIcon = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?:0)
            var result = makeBlackPixelsTransparent(userIcon!!)
            var profilePic: ImageView = findViewById(R.id.profilePic)
            profilePic.setImageBitmap(result)
        }

        // enabling changing icon
        val changeIcon: FloatingActionButton = findViewById(R.id.changePic)
        val changeIconIntent = Intent(this@UserSetting, SelfieSegmentation::class.java)
        changeIconIntent.putExtra("host", host)
        changeIconIntent.putExtra("lobbyCode", lobbyCode)
        changeIcon.setOnClickListener{ startActivity(changeIconIntent) }

        val confirmBtn: Button = findViewById(R.id.confirmBtn)
        confirmBtn.setOnClickListener { if (host) { hostConfirm(userIcon) } else { userConfirm(userIcon) }}

    }

    private fun userConfirm(userIcon: Bitmap?) {
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        if (username.isBlank()) {
            Toast.makeText(this@UserSetting, "Please enter a username", Toast.LENGTH_SHORT).show()
        } else {
            val query = database.getReference("gameSessions")
                .orderByChild("sessionId")
                .equalTo(lobbyCode)

            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        val gameSessionSnapshot = dataSnapshot.children.first()
                        val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                        if (gameSession != null) {
                            for (player in gameSession.players){
                                if (username == player.userName) {
                                    Toast.makeText(this@UserSetting, "Username already taken in game", Toast.LENGTH_SHORT).show()
                                    return
                                }
                            }
                            val newPlayer = PlayerClass(username, false, 0.0, 0.0, false, false)
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.add(newPlayer)


                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnSuccessListener {
                                val intent = Intent(this@UserSetting, Lobby::class.java)
                                intent.putExtra("lobby_key", lobbyCode)

                                if (userIcon != null) {
                                    // compress the bitmap
                                    val stream = ByteArrayOutputStream()
                                    userIcon?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    val byteArray = stream.toByteArray()

                                    // pass the bitmap to next activity
                                    intent.putExtra("userIcon", byteArray)
                                }

                                startActivity(intent)
                            }.addOnFailureListener {
                                Toast.makeText(this@UserSetting, "Error joining game", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@UserSetting, "Unexpected Error", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@UserSetting, "Invalid Lobby Code", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(this@UserSetting, "Error fetching data", Toast.LENGTH_SHORT)
                        .show()
                }
            })
        }
    }
    private fun hostConfirm(userIcon: Bitmap?) {
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        if (username.isBlank()) {
            Toast.makeText(this@UserSetting, "Please enter a username", Toast.LENGTH_SHORT).show()
        } else {
            val intent: Intent = Intent(this@UserSetting,NewGameSettings::class.java)

            if (userIcon != null) {
                // compress the bitmap
                val stream = ByteArrayOutputStream()
                userIcon?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()

                // pass the bitmap to next activity
                intent.putExtra("userIcon", byteArray)
            }

            intent.putExtra("username_key", username)
            startActivity(intent)
        }
    }

    private fun makeBlackPixelsTransparent(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixelColor = bitmap.getPixel(x, y)

                // Check if the pixel is fully black (R = 0, G = 0, B = 0)
                if (Color.red(pixelColor) == 0 && Color.green(pixelColor) == 0 && Color.blue(pixelColor) == 0) {
                    // Set the alpha channel to 0 (fully transparent)
                    resultBitmap.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    // Copy the pixel as it is
                    resultBitmap.setPixel(x, y, pixelColor)
                }
            }
        }

        return resultBitmap
    }

}
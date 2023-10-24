package com.example.hideandseek

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.time.LocalTime

class UserSetting : AppCompatActivity() {
    private var host: Boolean = false
    private var lobbyCode: String? = null
    private var userIcon: Bitmap? = null
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_setting)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        // receive the info and user icon if any value sent from previous activity
        host = intent.getBooleanExtra("host", false)
        lobbyCode = intent.getStringExtra("lobbyCode")

        // enabling changing icon
        val changeIcon: FloatingActionButton = findViewById(R.id.changePic)
        val resultLauncher =
            this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val byteArray = result.data?.getByteArrayExtra("userIcon")

                    // show the selfie segmentation if available
                    if (byteArray != null) {
                        userIcon = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?:0)
                        var result = makeBlackPixelsTransparent(userIcon!!)
                        var profilePic: ImageView = findViewById(R.id.profilePic)
                        profilePic.setImageBitmap(result)
                    }
                }
            }

        changeIcon.setOnClickListener{
            NetworkUtils.checkConnectivityAndProceed(this) {
                val changeIconIntent = Intent(this@UserSetting, SelfieSegmentation::class.java)
                resultLauncher.launch(changeIconIntent)
            }
        }

        val confirmBtn: Button = findViewById(R.id.confirmBtn)
        confirmBtn.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (host) {
                    hostConfirm(userIcon)
                } else {
                    userConfirm(userIcon)
                }
            }
        }

    }

    private fun userConfirm(userIcon: Bitmap?) {
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        var randomNum = ((0..9999).random())
        var playerCode = String.format("%04d",randomNum)

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

                            val newPlayer = PlayerClass(username, false, 0.0, 0.0, false, false, playerCode, LocalTime.now().toString(), "In Lobby")
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.add(newPlayer)

                            val codes = listOf<String>().toMutableList()
                            for (player in gameSession.players){
                                codes.add(player.playerCode)
                            }
                            while(codes.contains(playerCode)){
                                randomNum = ((0..9999).random())
                                playerCode = String.format("%04d",randomNum)
                            }

                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnSuccessListener {
                                val intent = Intent(this@UserSetting, Lobby::class.java)
                                intent.putExtra("lobby_key", lobbyCode)
                                intent.putExtra("username_key", username)
                                intent.putExtra("isSeeker", false)
                                intent.putExtra("playerCode", playerCode)

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
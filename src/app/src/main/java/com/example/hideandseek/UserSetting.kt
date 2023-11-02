package com.example.hideandseek

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalTime

class UserSetting : AppCompatActivity() {
    private var host: Boolean = false
    private var lobbyCode: String? = null
    private var uri: String? = null
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
                    // show the progress bar
                    val loading = this.findViewById<ProgressBar>(R.id.loading)
                    loading.visibility = View.VISIBLE
                    uri = result.data?.getStringExtra("uri")

                    // show the selfie segmentation if available
                    if (uri != null) {
                        selfieSegmentation(uri!!)
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
                    hostConfirm(uri)
                } else {
                    userConfirm(uri)
                }
            }
        }

    }

    private fun userConfirm(uri: String?) {
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

                                if (uri != null) {
                                    // pass the bitmap to next activity
                                    intent.putExtra("uri", uri)
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
    private fun hostConfirm(uri: String?) {
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        if (username.isBlank()) {
            Toast.makeText(this@UserSetting, "Please enter a username", Toast.LENGTH_SHORT).show()
        } else {
            val intent: Intent = Intent(this@UserSetting,NewGameSettings::class.java)

            if (uri != null) {
                // pass the bitmap to next activity
                intent.putExtra("uri", uri)
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

    private fun selfieSegmentation(output: String) {
        try {
            val uri = Uri.parse(output)
            var image: InputImage = InputImage.fromFilePath(this@UserSetting, uri)

            // configure segmenter
            val options =
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
            val segmenter = Segmentation.getClient(options)

            // process the image
            segmenter.process(image)
                .addOnSuccessListener { results ->
                    val mask = results.buffer
                    val maskWidth = results.width
                    val maskHeight = results.height

                    try {
                        val inputStream = contentResolver.openInputStream(uri!!)
                        var userPhoto =
                            Drawable.createFromStream(inputStream, uri.toString())

                        // convert user image into bitmap and retrieve the foreground
                        var bitmapImage = userPhoto?.toBitmap(maskWidth, maskHeight)
                        var copyBitmap = bitmapImage?.copy(Bitmap.Config.ARGB_8888, true)

                        val threshold = 0.92
                        for (y in 0 until maskHeight) {
                            for (x in 0 until maskWidth) {
                                val foregroundConfidence = mask.float
                                if (foregroundConfidence < threshold) {
                                    copyBitmap?.setPixel(x, y, Color.TRANSPARENT)
                                }
                            }
                        }

                        // display result
                        var profilePic: ImageView = findViewById(R.id.profilePic)
                        profilePic.setImageBitmap(copyBitmap)

                        val loading = this.findViewById<ProgressBar>(R.id.loading)
                        loading.visibility = View.INVISIBLE

                    } catch (e: FileNotFoundException) {
                        Log.e("File", "File Absent: $e")
                    }

                }
                .addOnFailureListener { e ->
                    Log.e("Segmentation", "Segmentation failed: $e")
                }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}
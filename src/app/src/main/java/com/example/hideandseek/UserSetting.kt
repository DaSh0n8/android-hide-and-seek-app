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
import java.io.ByteArrayOutputStream

class UserSetting : AppCompatActivity() {
    private var host: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_setting)

        // receive the info and user icon if any value sent from previous activity
        host = intent.getBooleanExtra("host", false)
        val origin: String? = intent.getStringExtra("origin")
        var userIcon: Bitmap? = null

        if (origin == "user_setting") {
            val byteArray = intent.getByteArrayExtra("UserIcon")
            userIcon = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?:0)
            var test = makeBlackPixelsTransparent(userIcon)
            var profilePic: ImageView = findViewById(R.id.profilePic)
            profilePic.setImageBitmap(test)
        }

        // enabled changing icon
        val changeIcon: FloatingActionButton = findViewById(R.id.changePic)
        val changeIconIntent = Intent(this@UserSetting, SelfieSegmentation::class.java)
        changeIcon.setOnClickListener{ startActivity(changeIconIntent) }

        val confirmBtn: Button = findViewById(R.id.confirmBtn)
        confirmBtn.setOnClickListener { confirmUserDetails(userIcon) }

    }

    private fun confirmUserDetails(userIcon: Bitmap?) {
        val usernameInput: EditText = findViewById(R.id.username_input)
        val username: String = usernameInput.text.toString()

        if (username.isBlank()) {
            Toast.makeText(this@UserSetting, "Please enter a username", Toast.LENGTH_SHORT).show()
        } else {
            val intent: Intent = if (host) {
                Intent(this@UserSetting,NewGameSettings::class.java)
            } else {
                Intent(this@UserSetting,JoinGame::class.java)
            }

            if (userIcon != null) {
                // compress the bitmap
                val stream = ByteArrayOutputStream()
                userIcon?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()

                // pass the bitmap to next activity
                intent.putExtra("UserIcon", byteArray)
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
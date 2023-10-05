package com.example.hideandseek

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream

class UserSetting : AppCompatActivity() {
    // receive from previous section
    private val host: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_setting)

        // receive the user icon if any value sent from previous activity
        val bundle = intent.extras
        var userIcon: Bitmap? = null
        if (bundle != null) {
            val byteArray = intent.getByteArrayExtra("UserIcon")
            userIcon = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?:0)
            var imageView: ImageView = findViewById(R.id.profilePic)
            imageView.setImageBitmap(userIcon)
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
}
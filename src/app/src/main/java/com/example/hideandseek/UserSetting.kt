package com.example.hideandseek

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class UserSetting : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_setting)

        // receive the user icon if any value sent from previous activity
        val bundle = intent.extras
        if (bundle != null) {
            val byteArray = intent.getByteArrayExtra("UserIcon")
            val userIcon = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?:0)
            var imageView: ImageView = findViewById(R.id.profilePic)
            imageView.setImageBitmap(userIcon)
        }

    }
}
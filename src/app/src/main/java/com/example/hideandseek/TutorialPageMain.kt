package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class TutorialPageMain : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial_page)
        val button : ImageButton = findViewById(R.id.right_button)
        val exit : ImageButton = findViewById(R.id.close_btn2)
        button.setOnClickListener{
            val intent = Intent(this@TutorialPageMain, BasicGameTutorial::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        exit.setOnClickListener {

            val intent = Intent(this@TutorialPageMain, HomeScreen::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
        }
    }
}
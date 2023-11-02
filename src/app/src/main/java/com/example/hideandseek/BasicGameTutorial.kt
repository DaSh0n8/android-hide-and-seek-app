package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class BasicGameTutorial : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_game_tutorial)
        val left : ImageButton = findViewById(R.id.left_btn)
        val right : ImageButton = findViewById(R.id.right_btn)
        val exit : ImageButton = findViewById(R.id.close_btn3)
        left.setOnClickListener{
            val intent = Intent(this@BasicGameTutorial, TutorialPageMain::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        right.setOnClickListener {
            val intent = Intent(this@BasicGameTutorial, StartGameTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        exit.setOnClickListener {

            val intent = Intent(this@BasicGameTutorial, HomeScreen::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
        }
    }
    override fun finish(){
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
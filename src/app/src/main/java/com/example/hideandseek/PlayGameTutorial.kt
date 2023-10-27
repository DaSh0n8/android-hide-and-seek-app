package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class PlayGameTutorial : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_game_tutorial)
        val left : ImageButton = findViewById(R.id.left_button4)
        val right : ImageButton = findViewById(R.id.right_button4)
        val exit : ImageButton = findViewById(R.id.close_btn7)
        left.setOnClickListener{
            val intent = Intent(this@PlayGameTutorial, ChooseGameSettingsTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        right.setOnClickListener {
            val intent = Intent(this@PlayGameTutorial, EndGameTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        exit.setOnClickListener {

            val intent = Intent(this@PlayGameTutorial, HomeScreen::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
        }
    }
    override fun finish(){
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
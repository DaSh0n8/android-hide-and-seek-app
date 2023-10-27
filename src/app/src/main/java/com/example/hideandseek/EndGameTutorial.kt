package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class EndGameTutorial : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_end_game_tutorial)
        val left : ImageButton = findViewById(R.id.left_arrow)
        val exit : ImageButton = findViewById(R.id.close_btn1)
        left.setOnClickListener{
            val intent = Intent(this@EndGameTutorial, PlayGameTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        exit.setOnClickListener {

            val intent = Intent(this@EndGameTutorial, HomeScreen::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
        }
    }
    override fun finish(){
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
package com.example.hideandseek

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class ChooseGameSettingsTutorial : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_game_settings_tutorial)
        val left : ImageButton = findViewById(R.id.left_button3)
        val right : ImageButton = findViewById(R.id.right_button3)
        val exit : ImageButton = findViewById(R.id.close_btn5)
        left.setOnClickListener{
            val intent = Intent(this@ChooseGameSettingsTutorial, StartGameTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        right.setOnClickListener {
            val intent = Intent(this@ChooseGameSettingsTutorial, LobbyTutorial::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        exit.setOnClickListener {
            val intent = Intent(this@ChooseGameSettingsTutorial, HomeScreen::class.java)
            //intent.putExtra("host", true)
            startActivity(intent)
        }
    }
    override fun finish(){
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
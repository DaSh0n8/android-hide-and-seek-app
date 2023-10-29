package com.example.hideandseek

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        // get access location permission
        val locationHelper = LocationHelper(this, 1)
        locationHelper.askPermission()

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (locationHelper.checkLocationPermission()) {
                    val intent = Intent(this@HomeScreen, UserSetting::class.java)
                    intent.putExtra("host", true)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@HomeScreen, "Location Permission Required!", Toast.LENGTH_SHORT).show()
                    openAppSettings()
                }
            }
        }

        val joinGameButton: Button = findViewById(R.id.joinGameButton)
        joinGameButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (locationHelper.checkLocationPermission()) {
                    val intent = Intent(this@HomeScreen, JoinGame::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@HomeScreen, "Location Permission Required!", Toast.LENGTH_SHORT).show()
                    openAppSettings()
                }
            }
        }

        val error = intent.getStringExtra("error")
        if (error != null) {
            Toast.makeText(this@HomeScreen, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", this.packageName, null)
        this.startActivity(intent)
    }


}
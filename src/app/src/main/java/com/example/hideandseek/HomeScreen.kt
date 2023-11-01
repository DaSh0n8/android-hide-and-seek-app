package com.example.hideandseek

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class HomeScreen : AppCompatActivity() {

    private lateinit var lightSensor: LightSensor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        // get access location permission
        val locationHelper = LocationHelper(this, 1)
        locationHelper.askPermission()

        lightSensor = LightSensor(this)

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                if (locationHelper.checkLocationPermission()) {
                    val intent = Intent(this@HomeScreen, UserSetting::class.java)
                    intent.putExtra("host", true)
                    startActivity(intent)
                } else {
                    connectInternetDialog()
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
                    connectInternetDialog()
                }
            }
        }


//        val nightModeSwitch: SwitchCompat = findViewById(R.id.nightModeSwitch)
//        nightModeSwitch.setOnCheckedChangeListener{ buttonView, isChecked -> {
//            if (isChecked) {
//                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//            } else {
//                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//            }
//          }
//        }

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

    private fun connectInternetDialog() {
        createCustomDialog(
            "Sorry...",
            "Location Permission Required!",
            "OK"
        ) {
            openAppSettings()
        }
    }

    private fun createCustomDialog(
        titleText: String,
        messageText: String,
        positiveButtonText: String,
        positiveButtonAction: () -> Unit
    ) {
        val builder = AlertDialog.Builder(this)

        val customTitleView = layoutInflater.inflate(R.layout.dialog_title, null)
        val customMessageView = layoutInflater.inflate(R.layout.dialog_message, null)

        // Set the title text dynamically
        (customTitleView.findViewById<TextView>(R.id.title)).text = titleText

        // Set the message text dynamically
        (customMessageView.findViewById<TextView>(R.id.message)).text = messageText

        with(builder) {
            setCustomTitle(customTitleView) // Set the custom title view
            setView(customMessageView)     // Set the custom message view
            setPositiveButton(positiveButtonText) { dialog, _ ->
                positiveButtonAction()
                dialog.dismiss()
            }
            val dialog = create()
            dialog.setOnShowListener { dialogInterface ->
                val okButton = (dialogInterface as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                okButton.setTextColor(ContextCompat.getColor(this@HomeScreen, R.color.blue))
            }
            dialog.show()
        }
    }


}
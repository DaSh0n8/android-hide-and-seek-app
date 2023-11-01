package com.example.hideandseek

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorEvent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat.getSystemService
import android.util.Log

class LightSensor : SensorEventListener {

    private var disabled: Boolean = false
    private var context: Context
    private lateinit var sensorManager: SensorManager
    private var brightness: Sensor? = null

    constructor(context: Context) : super() {
        this.context = context
        enableSensor()
    }

    private fun enableSensor() {
        // Register a listener for the sensor.
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        brightness = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)


        sensorManager.registerListener(this, brightness, SensorManager.SENSOR_DELAY_NORMAL)
    }

    public fun disableSensor() {
        // Unregister the sensor when the activity pauses.
        sensorManager.unregisterListener(this)
        sensorManager.unregisterListener(this)
    }


    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        return
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor == null) {
            return
        }
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            val luxValue = event.values[0]
            Log.v("sensor..", "sensor$luxValue")
            if (luxValue.toInt() > 1000) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    fun getDisabled(): Boolean {
        return disabled
    }
}
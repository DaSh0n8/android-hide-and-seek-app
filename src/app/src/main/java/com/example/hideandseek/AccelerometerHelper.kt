
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class LinearAccelerationHelper(
    private val context: Context,
    private val listener: LinearAccelerationListener
) {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelerationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    fun startListening() {
        linearAccelerationSensor?.let {
            sensorManager.registerListener(linearAccelerationEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(linearAccelerationEventListener)
    }

    private val linearAccelerationEventListener = object : SensorEventListener {
        private val MOVEMENT_THRESHOLD = 5.0f

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val acceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())

                if (acceleration > MOVEMENT_THRESHOLD) {
                    // Notify the listener about the detection
                    listener.onRunningDetected()
                }
            }
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            // Not yet implemented
        }
    }

    interface LinearAccelerationListener {
        fun onRunningDetected()
    }
}

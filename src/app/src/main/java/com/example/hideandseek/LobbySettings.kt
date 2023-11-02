package com.example.hideandseek

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hideandseek.NetworkUtils.Companion.checkConnectivityAndProceed
import com.example.hideandseek.databinding.NewGameSettingsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.slider.Slider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.Duration
import java.time.LocalTime

class LobbySettings : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var binding: NewGameSettingsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var locationHelper: LocationHelper
    private lateinit var connectTimer: CountDownTimer
    private lateinit var lobbySettingsListener: ValueEventListener

    // default geofence radius
    private var geofenceRadius = 100.0
    private var userLatLng: LatLng = LatLng(0.0, 0.0)

    private var currentLobbyCode: String? = null
    private var currentUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = NewGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedLobbyCode: String? = intent.getStringExtra("lobby_code_key")
        val receivedUsername: String? = intent.getStringExtra("username_key")
        currentLobbyCode = receivedLobbyCode
        currentUserName = receivedUsername
        intent.getBooleanExtra("host", false)
        val lobbyHeader = findViewById<TextView>(R.id.titleText)
        lobbyHeader.text = "Change Game Setting"

        // Request location updates
        locationHelper = LocationHelper(this, (1*60*1000))
        locationHelper.requestLocationUpdates { location ->
            Log.d("See location", location.toString())
            userLatLng = LatLng(location.latitude, location.longitude)
            updateMap(userLatLng, map)
        }

        // obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        // observe the changes on the slider
        val discreteSlider: Slider = findViewById(R.id.radiusSlider)
        discreteSlider.addOnChangeListener { _, radius, _ ->
            // update the visible geofence on map as it goes
            geofenceRadius = radius.toDouble()
            updateMap(userLatLng, map)
        }

        val createGameButton: Button = findViewById(R.id.btnStartGame)
        createGameButton.setOnClickListener {
            checkConnectivityAndProceed(this) {
                confirmSettingsClicked(receivedLobbyCode)
            }
        }

        val cancelBtn: Button = findViewById(R.id.btnCancelGame)
        cancelBtn.setOnClickListener { finish() }

        loadSettingsInFields(receivedLobbyCode)
    }

    private fun loadSettingsInFields(lobbyCode: String?){
        if (lobbyCode == null){
            return
        }
        val hidingTimeInput: EditText = findViewById(R.id.editHidingTime)
        val updateIntervalInput: EditText = findViewById(R.id.editUpdateInterval)
        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val radiusInput: Slider = findViewById(R.id.radiusSlider)

        val gameSessionRef = database.getReference("gameSessions")
        val query = gameSessionRef.orderByChild("sessionId").equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val gameSessionSnapshot = snapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    gameSession?.let {
                        hidingTimeInput.setText(it.hidingTime.toString())
                        updateIntervalInput.setText(it.updateInterval.toString())
                        gameTimeInput.setText(it.gameLength.toString())
                        radiusInput.value = it.radius.toFloat()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LobbySettings, "Error loading game settings", Toast.LENGTH_SHORT).show()
            }
        })
    }


    /**
     * Update game session with user input values
     */
    private fun confirmSettingsClicked(receivedLobbyCode: String?) {
        if (receivedLobbyCode == null) {
            return
        }
        val hidingTimeInput: EditText = findViewById(R.id.editHidingTime)
        val hidingTime: String = hidingTimeInput.text.toString().trim()

        val updateIntervalInput: EditText = findViewById(R.id.editUpdateInterval)
        val updateInterval: String = updateIntervalInput.text.toString().trim()

        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val gameTime: String = gameTimeInput.text.toString().trim()

        val radiusInput: Slider = findViewById(R.id.radiusSlider)
        val geofenceRadius: Float = radiusInput.value

        if (updateInterval.isBlank() || hidingTime.isBlank() || gameTime.isBlank()) {
            Toast.makeText(this@LobbySettings, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        } else if (updateInterval.toInt() < 1 || hidingTime.toInt() < 1 || gameTime.toInt() < 1){
            Toast.makeText(this@LobbySettings, "Input values have to be more than 0", Toast.LENGTH_SHORT).show()
            return
        }

        val query = database.getReference("gameSessions").orderByChild("sessionId").equalTo(receivedLobbyCode)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()

                    val updatedGameSession = mapOf(
                        "gameStatus" to "ongoing",
                        "gameLength" to gameTime.toInt(),
                        "updateInterval" to updateInterval.toInt(),
                        "hidingTime" to hidingTime.toInt(),
                        "radius" to geofenceRadius.toInt(),
                        "geofenceLat" to userLatLng.latitude,
                        "geofenceLon" to userLatLng.longitude
                    )

                    gameSessionSnapshot.ref.updateChildren(updatedGameSession)
                        .addOnSuccessListener {
                            Toast.makeText(this@LobbySettings, "Game configurations updated", Toast.LENGTH_SHORT).show()
                            locationHelper.stopUpdate()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@LobbySettings, "Error updating game settings", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this@LobbySettings, "Lobby code not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@LobbySettings, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Manipulates the map once available with latest values.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
    }

    /**
     * Reflect the user's location on the map with virtual geofence drawn.
     */
    private fun updateMap(user: LatLng, googleMap: GoogleMap) {
        // clear previous drawings the map
        map = googleMap
        map.clear()

        // convert the drawable user icon to a Bitmap
        val userIconBitmap = scaleBitmap(BitmapFactory.decodeResource(resources, R.drawable.usericon), 35)

        // add the marker and adjust the view
        map.addMarker(
            MarkerOptions()
                .position(user)
                .icon(BitmapDescriptorFactory.fromBitmap(userIconBitmap))
        )
        map.setMinZoomPreference(15F)
        map.moveCamera(CameraUpdateFactory.newLatLng(user))

        // set the map style
        map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(this, R.raw.gamemap_lightmode)
        )

        // add the geofence
        map.addCircle(
            CircleOptions()
                .center(user)
                .radius(geofenceRadius) // Radius in meters
                .strokeColor(Color.RED) // Circle border color
                .fillColor(Color.argb(60, 220, 0, 0)) // Fill color with transparency
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                locationHelper.requestLocationUpdates { location ->
                    userLatLng = LatLng(location.latitude, location.longitude)
                    updateMap(userLatLng, map)
                }
            } else {
                // Permission denied, handle accordingly
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Converts the drawables from vector form to Bitmap form.
     */
    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onStart() {
        super.onStart()
        var tickCounter = 0
        val interval = 5
        connectTimer = object: CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (tickCounter == interval) {
                    if (NetworkUtils.checkForInternet(this@LobbySettings)){
                        acknowledgeOnline(currentLobbyCode, currentUserName)
                        checkPlayerActivity(currentLobbyCode)
                    } else {
                        val intent = Intent(this@LobbySettings, HomeScreen::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        Toast.makeText(this@LobbySettings, "You have been disconnected from the lobby", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                        finish()
                    }

                    tickCounter = 0
                }
                tickCounter++
            }
            override fun onFinish() {

            }
        }.start()
    }

    override fun onStop() {
        super.onStop()
        connectTimer.cancel()
    }

    private fun acknowledgeOnline(lobbyCode: String?, username: String?) {
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()

                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = database.getReference("gameSessions").child(gameSessionSnapshot.key!!)

                if (gameSession != null) {
                    // update user's last update time
                    val players = gameSession.players.toMutableList()
                    for ((index, p) in players.withIndex()) {
                        if (p.userName == username) {
                            val currTime = LocalTime.now().toString()
                            ref.child("players").child(index.toString()).child("lastUpdated").setValue(currTime)
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    private fun checkPlayerActivity(lobbyCode: String?) {
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                    if (gameSession != null) {
                        val currentTime = LocalTime.now()
                        for (player in gameSession.players) {
                            val lastUpdatedTime = LocalTime.parse(player.lastUpdated)
                            val duration = Duration.between(lastUpdatedTime, currentTime)
                            if (duration.seconds > 30) {
                                kickPlayer(lobbyCode, player.userName)
                                Log.e("checkPlayerActivity", "Kicking the player ${player.userName}")
                            }
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    private fun kickPlayer(lobbyCode: String?, playerToKick: String?) {
        val query = database.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    if (gameSession != null) {
                        val playerIsHost = gameSession.players.any { it.userName == playerToKick && it.host }

                        if (playerIsHost) {
                            gameSessionSnapshot.ref.removeValue().addOnSuccessListener {
                                Toast.makeText(this@LobbySettings, "The game session has ended as the host left", Toast.LENGTH_SHORT).show()
                                finish()
                            }.addOnFailureListener {
                                Toast.makeText(this@LobbySettings, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val updatedPlayers = gameSession.players.toMutableList()
                            updatedPlayers.removeIf { it.userName == playerToKick }

                            gameSession.players = updatedPlayers
                            gameSessionSnapshot.ref.setValue(gameSession).addOnSuccessListener {
                                Toast.makeText(this@LobbySettings, "$playerToKick was removed due to inactivity", Toast.LENGTH_SHORT).show()
                            }.addOnFailureListener {
                                Toast.makeText(this@LobbySettings, "Unexpected Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@LobbySettings, "Unexpected Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@LobbySettings, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun returnHomeIntent(lobbyCode: String?, host: Boolean?, voluntary: Boolean) {
        val intent = Intent(this@LobbySettings, HomeScreen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        if (!host!! && !voluntary) {
            Toast.makeText(this@LobbySettings, "Host has left", Toast.LENGTH_SHORT).show()
        } else {
            intent.putExtra("lobby_key", lobbyCode)
        }
        startActivity(intent)
        finish()
    }

    private fun scaleBitmap(originalBitmap: Bitmap, targetSizeDp: Int): Bitmap {
        val resources = Resources.getSystem()

        // Convert dp to pixels
        val targetSizePixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            targetSizeDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        // Calculate the scale factor
        val scale = targetSizePixels.toFloat() / originalBitmap.width

        // Create a matrix for the scaling operation
        val matrix = android.graphics.Matrix()
        matrix.postScale(scale, scale)

        // Resize the bitmap
        return Bitmap.createBitmap(
            originalBitmap, 0, 0,
            originalBitmap.width, originalBitmap.height, matrix, true
        )
    }
}
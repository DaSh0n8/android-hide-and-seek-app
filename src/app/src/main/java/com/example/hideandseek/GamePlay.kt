package com.example.hideandseek

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hideandseek.databinding.GamePlayBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.util.Timer
import java.util.TimerTask


class GamePlay : AppCompatActivity(), OnMapReadyCallback {
    // default values
    private val defaultGameTime = minToMilli(1)
    private val defaultHideTime = minToMilli(1)
    private val defaultInterval = minToMilli(1)
    private val defaultRadius = 100

    // game play variables
    private var initLat = -37.809105
    private var initLon = 144.9609933
    private lateinit var userName: String
    private lateinit var lobbyCode: String
    private var gameTime = defaultGameTime
    private var hideTime = defaultHideTime
    private var updateInterval = defaultInterval
    private var geofenceRadius = defaultRadius

    private lateinit var map: GoogleMap
    private lateinit var binding: GamePlayBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var gameplayListener: ValueEventListener

    // Google's API for location services
    private var fusedLocationClient: FusedLocationProviderClient? = null

    // configuration of all settings of FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallBack: LocationCallback? = null
    private val Request_Code_Location = 22

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = GamePlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // receive data from previous activity
        lobbyCode = intent.getStringExtra("lobbyCode")!!
        userName = intent.getStringExtra("username")!!
        gameTime = minToMilli(intent.getIntExtra("gameLength", 1))
        hideTime = minToMilli(intent.getIntExtra("hidingTime", 1))
        updateInterval = minToMilli(intent.getIntExtra("updateInterval", 1))
        geofenceRadius = intent.getIntExtra("radius", defaultRadius)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        // query the db to get the user's session
        val reference = database.getReference("gameSessions")
        val query = reference.orderByChild("sessionId").equalTo(lobbyCode)
        var lastUpdate: TextView = findViewById(R.id.lastUpdate)
        lastUpdate.visibility = INVISIBLE

        // location API settings
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            updateInterval).build()
        locationCallBack = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult != null) {
                    Log.d("LocationTest", "Location updates")
                    locationResult.lastLocation?.let { uploadLoc(it, query) }
                } else {
                    Log.d("LocationTest", "Location updates fail: null")
                }
            }
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // hiding time for hiders
        var countDown: TextView = findViewById(R.id.playTime)
        var countDownValue: TextView = findViewById(R.id.playTimeValue)
        var hidingText: TextView = findViewById(R.id.hidingText)
        val hideTimer = object: CountDownTimer(hideTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) % 60
                val minutes = (millisUntilFinished / 1000) / 60
                countDownValue.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                // start the game
                countDown.text = "Play Time: "

                // remove overlay
                hidingText.visibility = GONE
                lastUpdate.visibility = VISIBLE

                // count down timer for game play
                val gameTimer = object: CountDownTimer(gameTime, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000) % 60
                        val minutes = (millisUntilFinished / 1000) / 60
                        countDownValue.text = String.format("%02d:%02d", minutes, seconds)
                    }

                    override fun onFinish() {
                        endGame()
                    }
                }
                // start the game by counting down
                gameTimer.start()

                // start showing the hiders location
                showUserLocation(query, gameTimer)
            }
        }
        hideTimer.start()
    }

    /**
     * Convert minute to milliseconds
     */
    private fun minToMilli(minute: Int): Long {
        return (minute * 60 * 1000).toLong()
    }

    /**
     * Show users' locations on the map.
     */
    private fun showUserLocation(query: Query, gameTimer: CountDownTimer) {
        // convert the drawable user icon to a Bitmap
        val userIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.self_user_icon)
        val hiderIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.user_icon)
        val eliminatedIcon = getBitmapFromVectorDrawable(this, R.drawable.eliminated)
        var lastUpdate: TextView = findViewById(R.id.lastUpdateValue)
        val timer = Timer()
        var minutePassed = -1

        // update the last update time
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    // Add 1 to the variable every minute
                    minutePassed++
                    lastUpdate.text = "$minutePassed minute(s) ago"
                }
            }
        }, 0, 60 * 1000)

        gameplayListener = query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                var hidersAvailable = false

                // reset the map before reflecting users' latest location
                map.clear()
                minutePassed = 0
                lastUpdate.text = "$minutePassed minute(s) ago"

                // draw geofence
                map.addCircle(
                    CircleOptions()
                        .center(LatLng(initLat, initLon))
                        .radius(geofenceRadius.toDouble()) // Radius in meters
                        .strokeColor(Color.RED) // Circle border color
                        .fillColor(Color.argb(60, 220, 0, 0)) // Fill color with transparency
                )

                if (gameSession != null) {
                    // get the players in the game session
                    val players = gameSession.players
                    // reflect hiders' latest locations on map
                    players.forEach{ player ->
                        val coordinates = LatLng(player.latitude!!, player.longitude!!)
                        val markerOptions = MarkerOptions().position(coordinates)

                        if (!player.seeker) {
                            if (player.eliminated) {
                                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(eliminatedIcon))
                            } else {
                                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(
                                    if (player.userName == userName) userIconBitmap
                                    else hiderIconBitmap))
                                hidersAvailable = true
                            }
                        } else if (player.userName == userName) {
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(userIconBitmap))
                        }

                        map.addMarker(markerOptions)
                    }
                }

                // end the game if all hiders are eliminated
                if (!hidersAvailable) {
                    timer.cancel()
                    gameTimer.cancel()
                    endGame()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GamePlay, "Error fetching data", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }


    /**
     * Manipulates the map once available.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateLocation()
    }

    /**
     * Obtain the last location of the user and perform update.
     */
    private fun updateLocation() {
        //if user grants permission
        if (ActivityCompat.checkSelfPermission(
                this@GamePlay,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient!!.requestLocationUpdates(
                locationRequest!!,
                locationCallBack!!,
                null
            )

            // get the last location
            fusedLocationClient!!.lastLocation
                .addOnSuccessListener(
                    this
                ) { location ->
                    if (location == null) {
                        Log.d("LocationTest", "null")
                    } else {
                        Log.d("LocationTest", "Success")
                    }
                }
        } else {
            //if user hasn't granted permission, ask for it explicitly
            ActivityCompat.requestPermissions(
                this@GamePlay,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                Request_Code_Location
            )
        }
    }

    /**
     * Upload user's coordinate to database
     */
    private fun uploadLoc(location: Location, query: Query) {
        // extract the coordinates
        val lat = location.latitude
        val lon = location.longitude
        val user = LatLng(lat, lon)

        // update in database
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                if (gameSession != null) {
                    val players = gameSession.players.toMutableList()
                    for (p in players) {
                        // update user's coordinates
                        if (p.userName == userName) {
                            p.latitude = lat
                            p.longitude = lon
                        }
                    }
                    // push to realtime database
                    gameSession.players = players
                    gameSessionSnapshot.ref.setValue(gameSession)
                }

                map.setMinZoomPreference(15F)
                map.moveCamera(CameraUpdateFactory.newLatLng(user))

                // set the map style
                map.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(this@GamePlay, R.raw.gamemap_lightmode)
                )
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request_Code_Location) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocation()
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

    /**
     * Calculate the results and initiate result page
     */
    private fun endGame() {
        // query the db to get the user's session
        val reference = database.getReference("gameSessions")
        val query = reference.orderByChild("sessionId").equalTo(lobbyCode)

        query.get().addOnSuccessListener{
            // get the game session
            var host = false
            val gameSessionSnapshot = it.children.first()
            val sessionId = gameSessionSnapshot.key.toString()
            val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
            var seekerWonGame = true

            if (gameSession != null) {
                val players = gameSession.players.toMutableList()
                for (p in players) {
                    // check if all players have been eliminated
                    if (!p.eliminated && !p.seeker) {
                        seekerWonGame = false

                    }

                    if (p.userName == userName) {
                        host = p.host
                    }
                }
                val result: String = if (seekerWonGame) {
                    "Seekers Won!"
                } else {
                    "Hiders Won!"
                }
                val gameOver = Intent(this@GamePlay, GameOver::class.java)
                gameOver.putExtra("result", result)
                gameOver.putExtra("lobbyCode", lobbyCode)
                gameOver.putExtra("sessionId", sessionId)
                gameOver.putExtra("username", userName)
                gameOver.putExtra("host", host)
                startActivity(gameOver)

                // turn off game play listener
                reference.removeEventListener(gameplayListener)
                finish()
            }
        }
    }
}
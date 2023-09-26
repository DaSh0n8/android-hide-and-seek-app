package com.example.hideandseek

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date


class GamePlay : AppCompatActivity(), OnMapReadyCallback {

    // need to fetch from "Lobby" activity
    private var lobbycode = "4407"
    private var userName = "Yao"
    private var gameTime = (10 * 60 * 1000).toLong()
    private var hideTime = (1 * 60 * 1000).toLong()

    private lateinit var map: GoogleMap
    private lateinit var binding: GamePlayBinding
    private lateinit var database: FirebaseDatabase

    // Google's API for location services
    private var fusedLocationClient: FusedLocationProviderClient? = null

    // configuration of all settings of FusedLocationProviderClient
    var locationRequest: LocationRequest? = null
    var locationCallBack: LocationCallback? = null
    private val Request_Code_Location = 22

    // interval in milliseconds for location updates
    private var updateInterval: Long = 1 * 60 * 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = GamePlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // start the firebase
        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        // query the db to get the user's session
        val reference = database.getReference("gameSessions")
        val query = reference.orderByChild("sessionId").equalTo(lobbycode)

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
        val hideTimer = object: CountDownTimer(hideTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) % 60
                val minutes = (millisUntilFinished / 1000) / 60
                countDownValue.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                // start the game
                countDown.text = "Play Time: "

                // start showing the hiders location
                showUserLocation(query)

                // start the game by counting down
                val timer = object: CountDownTimer(gameTime, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000) % 60
                        val minutes = (millisUntilFinished / 1000) / 60
                        countDownValue.text = String.format("%02d:%02d", minutes, seconds)
                    }

                    override fun onFinish() {
                        TODO()
                    }
                }
                timer.start()
            }
        }
        hideTimer.start()
    }

    /**
     * Show users' locations on the map.
     */
    private fun showUserLocation(query: Query) {
        // convert the drawable user icon to a Bitmap
        val userIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.self_user_icon)
        val hiderIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.user_icon)
        val eliminatedIcon = getBitmapFromVectorDrawable(this, R.drawable.eliminated)
        var lastUpdate: TextView = findViewById(R.id.lastUpdateValue)

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                // reset the map before reflecting users' latest location
                map.clear()

                if (gameSession != null) {
                    // get the players in the game session
                    val players = gameSession.players

                    // reflect hiders' latest locations on map
                    players.forEach{
                        val coordinates = LatLng(it.latitude!!, it.longitude!!)
                        if (!it.seeker) {
                            // check if user has been eliminated
                            if (it.eliminated) {
                                map.addMarker(
                                    MarkerOptions()
                                        .position(coordinates)
                                        .icon(BitmapDescriptorFactory.fromBitmap(eliminatedIcon))
                                )
                            } else {
                                map.addMarker(
                                    MarkerOptions()
                                        .position(coordinates)
                                        .icon(BitmapDescriptorFactory.fromBitmap(hiderIconBitmap))
                                )
                            }
                        } else if (it.userName == userName) {
                            map.addMarker(
                                MarkerOptions()
                                    .position(coordinates)
                                    .icon(BitmapDescriptorFactory.fromBitmap(userIconBitmap))
                            )
                        }
                    }
                }

                // update the last update time
                val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                val currentDate = sdf.format(Date())
                lastUpdate.text = currentDate
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
}
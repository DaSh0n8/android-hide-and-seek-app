package com.example.hideandseek

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hideandseek.databinding.NewGameSettingsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
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
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Random

class LobbySettings : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var binding: NewGameSettingsBinding
    private lateinit var database: FirebaseDatabase

    // default geofence radius
    private var geofenceRadius = 100.0

    // Google's API for location services
    private var fusedLocationClient: FusedLocationProviderClient? = null

    // configuration of all settings of FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallBack: LocationCallback? = null
    private val Request_Code_Location = 22

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = NewGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // location API settings
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()
        locationCallBack = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult != null) {
                    Log.d("LocationTest", "Location updates")
                    locationResult.lastLocation?.let { updateMap(it, map) }
                } else {
                    Log.d("LocationTest", "Location updates fail: null")
                }
            }
        }

        // start the firebase
        FirebaseApp.initializeApp(this)
        val databaseUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl)

        // obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // observe the changes on the slider
        val discreteSlider: Slider = findViewById(R.id.discreteSlider)
        discreteSlider.addOnChangeListener { _, radius, _ ->
            // update the visible geofence on map as it goes
            geofenceRadius = radius.toDouble()
            updateLocation(map)
        }

        val createGameButton: Button = findViewById(R.id.btnStartGame)
        val receivedLobbyCode: String? = intent.getStringExtra("lobby_code_key")
        createGameButton.setOnClickListener {
            confirmSettingsClicked(receivedLobbyCode)
        }
    }

    /**
     * Update game session with user input values
     */
    private fun confirmSettingsClicked(receivedLobbyCode: String?) {
        if (receivedLobbyCode == null) {
            return
        }
        val hidersNumberInput: EditText = findViewById(R.id.editHiders)
        val hidersNumber: String = hidersNumberInput.text.toString().trim()

        val seekersNumberInput: EditText = findViewById(R.id.editSeekers)
        val seekersNumber: String = seekersNumberInput.text.toString().trim()

        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val gameTime: String = gameTimeInput.text.toString().trim()

        val radiusInput: Slider = findViewById(R.id.discreteSlider)
        val geofenceRadius: Float = radiusInput.value

        if (seekersNumber.isBlank() || hidersNumber.isBlank() || gameTime.isBlank()) {
            Toast.makeText(this@LobbySettings, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        } else if (seekersNumber.toInt() >= hidersNumber.toInt()) {
            Toast.makeText(this@LobbySettings, "There needs to be more hiders than seekers", Toast.LENGTH_SHORT).show()
            return
        }

        val query = database.getReference("gameSessions").orderByChild("sessionId").equalTo(receivedLobbyCode)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()

                    val updatedGameSession = mapOf(
                        "status" to "ongoing",
                        "gameLength" to gameTime.toInt(),
                        "seekersNumber" to seekersNumber.toInt(),
                        "hidersNumber" to hidersNumber.toInt(),
                        "radius" to geofenceRadius.toInt()
                    )

                    gameSessionSnapshot.ref.updateChildren(updatedGameSession)
                        .addOnSuccessListener {
                            Toast.makeText(this@LobbySettings, "Game configurations updated", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LobbySettings, Lobby::class.java)
                            intent.putExtra("lobby_key", receivedLobbyCode)
                            startActivity(intent)
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
        updateLocation(map)
    }

    /**
     * Obtain the last location of the hider and update the map.
     */
    private fun updateLocation(googleMap: GoogleMap) {
        //if user grants permission
        if (ActivityCompat.checkSelfPermission(
                this@LobbySettings,
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
                        updateMap(location, googleMap) // if successful, update the UI
                    }
                }
        } else {
            //if user hasn't granted permission, ask for it explicitly
            ActivityCompat.requestPermissions(
                this@LobbySettings,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                Request_Code_Location
            )
        }
    }

    /**
     * Reflect the user's location on the map with virtual geofence drawn.
     */
    private fun updateMap(location: Location, googleMap: GoogleMap) {
        // clear previous drawings the map
        map = googleMap
        map.clear()

        // extract the coordinates
        val lat = location.latitude
        val lon = location.longitude
        val user = LatLng(lat, lon)

        // convert the drawable user icon to a Bitmap
        val userIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.user_icon)

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
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request_Code_Location) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocation(map)
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
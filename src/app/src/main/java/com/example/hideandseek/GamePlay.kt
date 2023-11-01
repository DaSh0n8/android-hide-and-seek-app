package com.example.hideandseek

import LinearAccelerationHelper
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hideandseek.databinding.GamePlayBinding
import com.example.hideandseek.databinding.GamePlayHiderBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.util.Timer
import java.util.TimerTask
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt


class GamePlay : AppCompatActivity(), OnMapReadyCallback {
    // default values
    private val defaultGameTime = minToMilli(1)
    private val defaultHideTime = minToMilli(1)
    private val defaultInterval = minToMilli(1)
    private val defaultRadius = 100

    // game play variables
    private lateinit var userName: String
    private lateinit var lobbyCode: String
    private lateinit var playerCode: String
    private var isSeeker: Boolean = false
    private var gameTime = defaultGameTime
    private var hideTime = defaultHideTime
    private var updateInterval = defaultInterval
    private var rapidInterval = (5 * 1000).toLong() // 5 seconds
    private var geofenceRadius = defaultRadius
    private lateinit var userLatLng: LatLng
    private var inGamePlayers: List<String>? = null
    private var hasTriggered: Boolean = false
    private var playersIcons: MutableMap<String, Bitmap> = mutableMapOf()
    private var lastLoc = mutableMapOf<String, LatLng>()
    private var lastStatus = mutableMapOf<String, Boolean>()
    private var mediaPlayer : MediaPlayer? = null
    private val disconnected = "disconnected"


    private lateinit var map: GoogleMap
    private lateinit var binding: GamePlayBinding
    private lateinit var bindingHiders: GamePlayHiderBinding
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var storageDb: FirebaseStorage
    private lateinit var locationHelper: LocationHelper
    private lateinit var gameplayListener: ValueEventListener
    private var accelerationHelper: LinearAccelerationHelper? = null
    private var accelerationListener: LinearAccelerationHelper.LinearAccelerationListener? = null

    // timer
    private lateinit var hideTimer: CountDownTimer
    private lateinit var gameTimer: CountDownTimer
    private lateinit var connectTimer: CountDownTimer

    // map zoom levels
    val mapZoom = mutableMapOf(
        100 to 17F,
        200 to 16F,
        300 to 16F,
        400 to 15F,
        500 to 15F
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isSeeker = intent.getBooleanExtra("isSeeker", false)
        mediaPlayer = MediaPlayer.create(this, R.raw.start_game)
        mediaPlayer?.start()
        mediaPlayer?.setOnCompletionListener (MediaPlayer.OnCompletionListener{
            mediaPlayer = MediaPlayer.create(this, R.raw.start)
        } )
        if (isSeeker){
            binding = GamePlayBinding.inflate(layoutInflater)
            setContentView(binding.root)
        }
        else{
            bindingHiders = GamePlayHiderBinding.inflate(layoutInflater)
            setContentView(bindingHiders.root)
        }

        // get firebase real time db
        val application = application as HideAndSeek
        realtimeDb = application.getRealtimeDb()
        storageDb = application.getStorageDb()

        // receive data from previous activity
        lobbyCode = intent.getStringExtra("lobbyCode")!!
        userName = intent.getStringExtra("username")!!
        playerCode= intent.getStringExtra("playerCode")!!
        gameTime = minToMilli(intent.getIntExtra("gameLength", 1))
        val triggerTime = (gameTime * 0.2).toLong()
        hideTime = minToMilli(intent.getIntExtra("hidingTime", 1))
        updateInterval = minToMilli(intent.getIntExtra("updateInterval", 1))
        geofenceRadius = intent.getIntExtra("radius", defaultRadius)

        // check connectivity
        confirmConnectivity(lobbyCode, userName)

        // retrieve players icons
        retrievePlayers(lobbyCode) { players ->
            inGamePlayers = players
            retrieveUserIcons(lobbyCode, inGamePlayers!!) { result ->
                playersIcons = result
            }
        }

        val legendBtn: Button = findViewById(R.id.legend_button)
        legendBtn.setOnClickListener { showLegend() }

        if (isSeeker){
            //eliminate player button setup
            val eliminate: Button = findViewById(R.id.eliminateBtn)
            val code: TextInputEditText = findViewById(R.id.textInputEditText)
            eliminate.setOnClickListener{
                eliminatePlayer(code.text.toString(), true)
                code.text?.clear()
            }
        }
        else{
            val code: TextView = findViewById(R.id.textCode)
            code.text = playerCode
        }

        // query the db to get the user's session
        val reference = realtimeDb.getReference("gameSessions")
        val query = reference.orderByChild("sessionId").equalTo(lobbyCode)
        val lastUpdate: TextView = findViewById(R.id.lastUpdate)
        lastUpdate.visibility = INVISIBLE

        // Request location updates
        locationHelper = LocationHelper(this, updateInterval)
        locationHelper.requestLocationUpdates { location ->
            Log.d("Location Updates", "Called")
            userLatLng = LatLng(location.latitude, location.longitude)
            uploadLoc(location, query)
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // hiding time for hiders
        val countDown: TextView = findViewById(R.id.playTime)
        val countDownValue: TextView = findViewById(R.id.playTimeValue)
        val hidingText: TextView = findViewById(R.id.hidingText)
        hideTimer = object: CountDownTimer(hideTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) % 60
                val minutes = (millisUntilFinished / 1000) / 60
                countDownValue.text = String.format("%02d:%02d", minutes, seconds)
                if (minutes.toDouble() == 0.0 && seconds.toDouble() == 3.0){
                    mediaPlayer?.start()
                }
            }


            override fun onFinish() {
                // start the game
                countDown.text = "Play Time: "

                // remove overlay
                hidingText.visibility = GONE
                lastUpdate.visibility = VISIBLE
                if (isSeeker) {
                    val eliminate: Button = findViewById(R.id.eliminateBtn)
                    eliminate.isEnabled = true
                    eliminate.setBackgroundColor(Color.parseColor("#005AFF"))
                    accelerationListener = null

                }

                // count down timer for game play
                gameTimer = object: CountDownTimer(gameTime, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        if (NetworkUtils.checkForInternet(this@GamePlay)) {
                            val seconds = (millisUntilFinished / 1000) % 60
                            val minutes = (millisUntilFinished / 1000) / 60
                            countDownValue.text = String.format("%02d:%02d", minutes, seconds)
                            if (minutes.toDouble() == 0.0 && seconds.toDouble() == 3.0){
                                mediaPlayer?.start()
                            }
                            mediaPlayer?.setOnCompletionListener (MediaPlayer.OnCompletionListener{
                                mediaPlayer?.release();
                            } )

                            // if only 20% time left, trigger the accelerometer
                            if (millisUntilFinished < triggerTime && !isSeeker && !hasTriggered) {
                                limitMovementDialog()
                                countDownValue.setTextColor(Color.RED)
                                limitMovement(query)
                                hasTriggered = true
                            }
                        } else {
                            disconnectedDialog()
                            gameTimer.cancel()
                        }
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
        val userIconBitmap = scaleBitmap(BitmapFactory.decodeResource(resources, R.drawable.usericon), 35)
        val eliminatedIcon = getBitmapFromVectorDrawable(this, R.drawable.eliminated)
        val disconnectedIcon = getBitmapFromVectorDrawable(this, R.drawable.disconnected)
        val lastUpdate: TextView = findViewById(R.id.lastUpdateValue)
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

                // draw geofence
                map.addCircle(
                    CircleOptions()
                        .center(LatLng(gameSession!!.geofenceLat, gameSession.geofenceLon))
                        .radius(geofenceRadius.toDouble()) // Radius in meters
                        .strokeColor(Color.RED) // Circle border color
                        .fillColor(Color.argb(60, 220, 0, 0)) // Fill color with transparency
                )

                if (gameSession != null) {
                    // get the players in the game session
                    val players = gameSession.players
                    // reflect hiders' latest locations on map
                    players.forEach{ player ->
                        // check the last update
                        val currTime = LocalTime.now()
                        val duration = minToMilli(Duration.between(LocalTime.parse(player.lastUpdated), currTime).toMinutes().toInt())
                        if (!player.eliminated && duration > 20000 && !player.seeker) {
                            setPlayerStatus(lobbyCode, player.userName, disconnected)
                        }

                        val coordinates = LatLng(player.latitude!!, player.longitude!!)
                        val markerOptions = MarkerOptions().position(coordinates).title(player.userName)

                        if (lastLoc[player.userName] != coordinates || lastStatus[player.userName] != player.eliminated) {
                            minutePassed = 0
                            lastUpdate.text = "$minutePassed minute(s) ago"

                            if (lastStatus[player.userName] != player.eliminated && player.userName == userName) {
                                deadDialog()
                            }

                            lastLoc[player.userName] =  coordinates
                            lastStatus[player.userName] = player.eliminated

                        }

                        val iconBitmap = if (!player.seeker && !player.eliminated && player.playerStatus != disconnected) {
                            hidersAvailable = true
                            playersIcons[player.userName] ?: userIconBitmap

                        } else if (player.seeker && player.userName == userName) {
                            playersIcons[player.userName] ?: userIconBitmap

                        } else if (!player.seeker && player.eliminated && player.playerStatus != disconnected) {
                            eliminatedIcon

                        } else if (!player.seeker && player.playerStatus == disconnected) {
                            disconnectedIcon

                        } else {
                            null
                        }

                        if (iconBitmap != null) {
                            iconBitmap.let {
                                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(it))
                            }

                            val marker = map.addMarker(markerOptions)

                            if (player.userName == userName) {
                                marker!!.showInfoWindow()
                            }
                        }

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
                val gameSession: GameSessionClass? = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = realtimeDb.getReference("gameSessions").child(gameSessionSnapshot.key!!)

                if (gameSession != null) {
                    // update user's coordinates
                    val players = gameSession.players
                    for ((index, p) in players.withIndex()) {
                        if (p.userName == userName) {
                            val path = ref.child("players").child(index.toString())
                            path.child("latitude").setValue(lat)
                            path.child("longitude").setValue(lon)

                            // eliminate self if exited the geofence
                            val geofenceLatLng = LatLng(gameSession!!.geofenceLat, gameSession.geofenceLon)
                            val leeway = 10 // 10 metres leeway
                            if(!isCoordinateInsideGeofence(user, geofenceLatLng, (geofenceRadius+leeway).toDouble())) {
                                eliminatePlayer(p.playerCode, false)
                                Toast.makeText(this@GamePlay, "You have been eliminated as you exited the game area!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // update the map accordingly
                map.setMinZoomPreference(mapZoom[geofenceRadius]!!)
                map.moveCamera(CameraUpdateFactory.newLatLng(user))
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
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
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

    /**
     * Calculate the results and initiate result page
     */
    private fun endGame() {
        // query the db to get the user's session
        val reference = realtimeDb.getReference("gameSessions")
        val query = reference.orderByChild("sessionId").equalTo(lobbyCode)

        query.get().addOnSuccessListener{
            // get the game session
            var host = false
            val gameSessionSnapshot = it.children.first()
            val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
            var seekerWonGame = true
            var isSeeker = false

            if (gameSession != null) {
                val players = gameSession.players
                for (p in players) {
                    // check if all players have been eliminated
                    if (!p.eliminated && !p.seeker && p.playerStatus != disconnected) {
                        seekerWonGame = false
                    }

                    if (p.userName == userName) {
                        host = p.host
                        isSeeker = p.seeker
                    }
                }

                // turn off listener
                if (!isSeeker && accelerationHelper != null) {
                    accelerationHelper!!.stopListening()
                }

                // Update the local GameSession object
                gameSession.players = players
                gameSession?.gameStatus = "ongoing"

                // Save the updated GameSession back to Firebase
                gameSessionSnapshot.ref.setValue(gameSession)
                    .addOnFailureListener {
                        Toast.makeText(this@GamePlay, "Error updating game status in Firebase", Toast.LENGTH_SHORT).show()
                    }

                val gameOver = Intent(this@GamePlay, GameOver::class.java)
                gameOver.putExtra("seekerWonGame", seekerWonGame)
                gameOver.putExtra("isSeeker", isSeeker)
                gameOver.putExtra("lobbyCode", lobbyCode)
                gameOver.putExtra("username", userName)
                gameOver.putExtra("host", host)

                lifecycleScope.launch {
                    delay(2000) // Delay for 3 seconds
                    startActivity(gameOver)
                    finish()
                }
            }
        }
    }

    /**
     * Eliminate a player from the game
     */
    private fun eliminatePlayer(code: String, voluntary: Boolean){
        if (code.isBlank()){
            Toast.makeText(this@GamePlay, "Please enter a code", Toast.LENGTH_SHORT).show()
        } else {
            val reference = realtimeDb.getReference("gameSessions")
            val query = reference.orderByChild("sessionId").equalTo(lobbyCode)
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(datasnapshot: DataSnapshot) {
                    if (datasnapshot.exists()) {
                        val gameSessionSnapshot = datasnapshot.children.first()
                        val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                        val ref =
                            realtimeDb.getReference("gameSessions").child(gameSessionSnapshot.key!!)
                        var existPlayer = false
                        var eliminatedUsername = ""

                        if (gameSession != null) {
                            val players = gameSession.players
                            for ((index, player) in players.withIndex()) {
                                if (code == player.playerCode) {
                                    eliminatedUsername = player.userName;
                                    if (player.seeker) {
                                        Toast.makeText(
                                            this@GamePlay,
                                            "$eliminatedUsername is a seeker and cannot be eliminated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (!player.eliminated) {
                                        val path = ref.child("players").child(index.toString())
                                        path.child("eliminated").setValue(true)
                                        existPlayer = true

                                    } else {
                                        Toast.makeText(
                                            this@GamePlay,
                                            "$eliminatedUsername has already been Eliminated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            if (existPlayer) {
                                if (voluntary) {
                                    eliminateDialog(eliminatedUsername)
                                }
                            } else {
                                Toast.makeText(
                                    this@GamePlay,
                                    "This user does not exist",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@GamePlay, "Database Error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Data retrieval error: ${error.message}")
                }

            })
        }
    }

    /**
     * Retrieve all user icons
     */
    private fun retrieveUserIcons(lobbyCode: String, playerList: List<String>, callback: (MutableMap<String, Bitmap>) -> Unit) {
        // get storage path
        val storageRef = storageDb.reference
        val userIcons: MutableMap<String, Bitmap> = mutableMapOf()

        // Counter to keep track of completed async calls
        var countDownLatch = playerList.size

        playerList.forEach { username ->
            val pathRef = storageRef.child("$lobbyCode/$username.jpg")
            // Check if the file exists before attempting to get bytes
            pathRef.downloadUrl.addOnSuccessListener {
                pathRef.getBytes(1_000_000)
                    .addOnSuccessListener { icons ->
                        val userIcon = BitmapFactory.decodeByteArray(icons, 0, icons?.size ?:0)
                        val result = makeBlackPixelsTransparent(userIcon!!)
                        userIcons[username] = scaleBitmap(result, 45)

                        // Decrement the counter
                        countDownLatch--

                        // Check if all async calls are completed
                        if (countDownLatch == 0) {
                            // All async calls are done, invoke the callback
                            callback(userIcons)
                        }
                    }
            }.addOnFailureListener {
                    // File doesn't exist
                    // Decrement the counter even if the file doesn't exist
                    countDownLatch--

                    // Check if all async calls are completed
                    if (countDownLatch == 0) {
                        // All async calls are done, invoke the callback
                        callback(userIcons)
                    }
            }
        }
    }


    /**
     * Retrieve all players usernames
     */
    private fun retrievePlayers(lobbyCode: String, callback: (List<String>) -> Unit) {
        // query the db to get the user's session
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        val playerList = mutableListOf<String>()

        query.get().addOnSuccessListener { snapshot ->
            // Check if there are any children
            if (snapshot.hasChildren()) {
                // get the game session
                val gameSessionSnapshot = snapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                if (gameSession != null) {
                    // Iterate through players and add to the list
                    for (p in gameSession.players) {
                        playerList.add(p.userName)
                        lastLoc[p.userName] = LatLng(p.latitude!!, p.longitude!!)
                        lastStatus[p.userName] = p.eliminated
                    }
                    callback(playerList)
                    return@addOnSuccessListener
                } else {
                    // Handle the case where parsing fails
                    Log.e("Retrieve Player Failed", "Failed to parse GameSession")
                    callback(emptyList())
                }
            } else {
                // Handle the case where no game session is found
                Log.e("Retrieve Player Failed", "No game session found for the given code")
                callback(emptyList())
            }
        }
            .addOnFailureListener { exception ->
                // Handle the failure case
                Log.e("Retrieve Player Failed", exception.toString())
                callback(emptyList())
            }
    }

    private fun makeBlackPixelsTransparent(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixelColor = bitmap.getPixel(x, y)

                // Check if the pixel is fully black (R = 0, G = 0, B = 0)
                if (Color.red(pixelColor) == 0 && Color.green(pixelColor) == 0 && Color.blue(pixelColor) == 0) {
                    // Set the alpha channel to 0 (fully transparent)
                    resultBitmap.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    // Copy the pixel as it is
                    resultBitmap.setPixel(x, y, pixelColor)
                }
            }
        }

        return resultBitmap
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

    private fun limitMovement(query: Query) {
        // initialise accelerometer and acceleration listener
        accelerationListener = object : LinearAccelerationHelper.LinearAccelerationListener {
            override fun onRunningDetected() {
                Toast.makeText(
                    this@GamePlay,
                    "Significant movement detected, your location will be constantly exposed!",
                    Toast.LENGTH_LONG
                ).show()
                locationHelper.setUpdateInterval(rapidInterval)
                locationHelper.requestLocationUpdates { location ->
                    Log.d("Location Updates", "Called")
                    userLatLng = LatLng(location.latitude, location.longitude)
                    uploadLoc(location, query)
                }
                accelerationHelper!!.stopListening()
            }
        }
        accelerationHelper = LinearAccelerationHelper(this, accelerationListener!!)
        accelerationHelper!!.startListening()
    }

    private fun returnHome() {
        val errorMessage = "You have been eliminated as you are disconnected from internet!"
        val intent = Intent(this@GamePlay, HomeScreen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("error", errorMessage)
        startActivity(intent)
        finish()
    }

    override fun onStop() {
        super.onStop()
        val reference = realtimeDb.getReference("gameSessions")
        reference.removeEventListener(gameplayListener)
        hideTimer.cancel()
        connectTimer.cancel()
        gameTimer.cancel()
        locationHelper.stopUpdate()
    }

    /**
     * Disallow user from leaving the game by pressing back button during game
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "You cannot leave the game midway!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Function to allow user to update their last online time
     */
    private fun acknowledgeOnline(lobbyCode: String?, username: String?) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = realtimeDb.getReference("gameSessions").child(gameSessionSnapshot.key!!)

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

    /**
     * Function to ensure user connectivity
     */
    private fun confirmConnectivity(lobbyCode: String?, username: String?) {
        var tickCounter = 0
        val checkpoint = 10
        connectTimer = object: CountDownTimer(hideTime + gameTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (tickCounter == checkpoint) {
                    acknowledgeOnline(lobbyCode, username)
                    tickCounter = 0
                }
                tickCounter++
            }
            override fun onFinish() {

            }
        }.start()
    }

    /**
     * Set player status
     */
    private fun setPlayerStatus(lobbyCode: String?, username: String?, status: String) {
        val query = realtimeDb.getReference("gameSessions")
            .orderByChild("sessionId")
            .equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // get the game session
                val gameSessionSnapshot = dataSnapshot.children.first()
                val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)
                val ref = realtimeDb.getReference("gameSessions").child(gameSessionSnapshot.key!!)

                if (gameSession != null) {
                    val players = gameSession.players.toMutableList()
                    for ((index, p) in players.withIndex()) {
                        if (p.userName == username) {
                            ref.child("players").child(index.toString()).child("playerStatus").setValue(status)
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Data retrieval error: ${databaseError.message}")
            }
        })
    }

    /**
     * Check if user is within the game play area
     */
    fun isCoordinateInsideGeofence(userLocation: LatLng, geofenceCenter: LatLng, radiusMeters: Double): Boolean {
        val earthRadius = 6371000.0 // Earth's radius in meters (approximately)

        val dLat = Math.toRadians(geofenceCenter.latitude - userLocation.latitude)
        val dLng = Math.toRadians(geofenceCenter.longitude - userLocation.longitude)

        val a = kotlin.math.sin(dLat / 2).pow(2) + cos(Math.toRadians(userLocation.latitude)) * cos(Math.toRadians(geofenceCenter.latitude)) * kotlin.math.sin(
            dLng / 2
        ).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distance = earthRadius * c // The distance between the two coordinates in meters
        return distance <= radiusMeters
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
                okButton.setTextColor(ContextCompat.getColor(this@GamePlay, R.color.blue))
            }
            dialog.show()
        }
    }

    private fun deadDialog() {
        createCustomDialog(
            "Whoops...",
            "You have been eliminated!",
            "OK"
        ) { /* Positive button action, if needed */ }
    }

    private fun limitMovementDialog() {
        createCustomDialog(
            "Be careful!!!",
            "Game ending, limit your movement or your location will be constantly exposed!",
            "OK"
        ) { /* Positive button action, if needed */ }
    }

    private fun disconnectedDialog() {
        createCustomDialog(
            "Sorry...",
            "You have been eliminated due to disconnection from the internet",
            "OK"
        ) {
            returnHome()
        }
    }

    private fun eliminateDialog(username: String?) {
        createCustomDialog(
            "Hider Eliminated",
            "$username has been eliminated!",
            "OK"
        ) { /* Positive button action for eliminateDialog */ }
    }

    private fun showLegend() {
        val legendDialog = Dialog(this)
        legendDialog.setContentView(R.layout.legend)
        legendDialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        val closeButton = legendDialog.findViewById<Button>(R.id.closeButton)
        closeButton.setOnClickListener {
            legendDialog.dismiss()
        }

        legendDialog.show()
    }
}
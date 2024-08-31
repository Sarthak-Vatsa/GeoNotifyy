package com.example.final_task1

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var handler: Handler
    private val refreshTime: Long = 5000 // 5000ms = 5s
    private lateinit var runnable: Runnable
    private lateinit var database: DatabaseReference
    private lateinit var googleMap: GoogleMap
    private lateinit var geofencePendingIntent: PendingIntent

    companion object {
        const val TAG = "MainActivity"
        const val LOCATION_REQUEST_CODE = 10001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        geofencingClient = LocationServices.getGeofencingClient(this)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().reference

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        handler = Handler()
        runnable = Runnable {
            checkLocationPermission()
            handler.postDelayed(runnable, refreshTime)
        }
        handler.post(runnable)

        // Initialize map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                LOCATION_REQUEST_CODE
            )
        } else {
            fetchGeofenceData()
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getUserLocation()
        } else {
            requestForPermissions()
        }
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val task = fusedLocationProviderClient.lastLocation
        task.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val longi = location.longitude

                val userLocation = LatLng(lat, longi)
                // Only move the camera if it hasn't been moved before
                if (!::googleMap.isInitialized || googleMap.cameraPosition.zoom == 14f) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 14f))
                }
            }
        }
    }

    private fun requestForPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            LOCATION_REQUEST_CODE
        )
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true
    }

    private fun fetchGeofenceData() {
        database.child("geofences").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (geofenceSnapshot in dataSnapshot.children) {
                    val name = geofenceSnapshot.key
                    val latitude = geofenceSnapshot.child("Latitude").getValue(Double::class.java) ?: 0.0
                    val longitude = geofenceSnapshot.child("Longitude").getValue(Double::class.java) ?: 0.0
                    val description = geofenceSnapshot.child("Description").getValue(String::class.java) ?: ""

                    Log.w(TAG, "latitude: ${latitude}, longitude: ${longitude}")
                    if (name != null) {
                        addMarkerAndGeofence(name, LatLng(latitude, longitude), description)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException())
            }
        })
    }

    private fun addMarkerAndGeofence(name: String, latLng: LatLng, description: String) {
        googleMap.addMarker(
            MarkerOptions().position(latLng).title(name)
        )
        googleMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(100.0)
                .strokeWidth(2f)
                .strokeColor(0xFF6495ED.toInt())
                .fillColor(0x446495ED)
        )

        val geofence = Geofence.Builder()
            .setRequestId(name)
            .setCircularRegion(latLng.latitude, latLng.longitude, 100.0f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(10) // 30 sec
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencePendingIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, GeofenceBroadcastReceiver::class.java).putExtra("description", description),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission is not granted")
            return
        }

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "Geofence added successfully")
            }
            addOnFailureListener { e ->
                Log.w(TAG, "Failed to add geofence: ", e)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                fetchGeofenceData()
            } else {
                Log.e(TAG, "Permission denied")
            }
        }
    }

    // Handling Geofence Transitions
    fun handleGeofenceTransition(geofenceTransition: Int, description: String) {
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> {
                findViewById<FrameLayout>(R.id.mapContainer).layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                findViewById<LinearLayout>(R.id.descriptionContainer).visibility = View.VISIBLE
                findViewById<TextView>(R.id.descriptionTextView).text = description
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                findViewById<FrameLayout>(R.id.mapContainer).layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                findViewById<LinearLayout>(R.id.descriptionContainer).visibility = View.GONE
            }
        }
    }
}

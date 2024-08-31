//DISPLAYING THE LVE LOCATION OF MULTIPLE USERS, UPDATING THEM CONSTANTLY IN FIREBASE AND DRAWING GEOFENCES AROUND UPDATED LOCATIONS.

package com.example.livelocation_23june

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.livelocation_23june.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var handler: Handler
    private val refreshTime: Long = 5000 // 5000ms = 5s
    private lateinit var runnable: Runnable
    private lateinit var deviceId: String

    companion object {
        const val LOCATION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        
        //Handler class runs the same thing multiple times like a loop (fetching current location multiple times = live location)
        handler = Handler()
        runnable = Runnable {
            checkLocationPermission()
            handler.postDelayed(runnable, refreshTime)
        }
        handler.post(runnable)
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
    }
    
    //Verify location permission
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
            mMap.uiSettings.isCompassEnabled = true
            mMap.uiSettings.isZoomControlsEnabled = true
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
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 14f))
                
                //writing device id,lats and longs to the DB
                deviceId = getDeviceId(this)
                val database = FirebaseDatabase.getInstance().reference
                val myRef = database.child("locations").child(deviceId)

                myRef.child("latitude").setValue(lat)
                myRef.child("longitude").setValue(longi)

                // Read from the database
                val myRef2 = database.child("locations")
                myRef2.addValueEventListener(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        mMap.clear() // Clear previous markers and circles
                        for (snap in snapshot.children) {
                            val name = snap.key
                            val latitude = snap.child("latitude").value
                            val longitude = snap.child("longitude").value

                            if (latitude != null && longitude != null) {
                                try {
                                    val latValue = latitude.toString().toDouble()
                                    val longValue = longitude.toString().toDouble()

                                    // Add marker to the map
                                    val location = LatLng(latValue, longValue)
                                    mMap.addMarker(MarkerOptions().position(location).title(name))

                                    // Add circle around the marker
                                    val circleOptions = CircleOptions()
                                        .center(location)
                                        .radius(25.0) // Radius in meters
                                        .strokeWidth(2f)
                                        .strokeColor(0xFF6495ED.toInt()) // Cornflower blue stroke color
                                        .fillColor(0x446495ED.toInt()) // Semi-transparent cornflower blue fill color
                                    mMap.addCircle(circleOptions)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to convert latitude/longitude value", e)
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(TAG, "Failed to read value.", error.toException())
                    }
                })
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                getUserLocation()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mMap.isMyLocationEnabled = true
    }
}

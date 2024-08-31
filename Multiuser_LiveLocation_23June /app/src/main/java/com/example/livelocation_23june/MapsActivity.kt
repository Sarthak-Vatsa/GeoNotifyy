package com.example.livelocation_23june

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.livelocation_23june.R.drawable
import com.example.livelocation_23june.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.util.UUID

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mMap: GoogleMap? = null
    private var binding: ActivityMapsBinding? = null
    var LOCATION_REQUEST_CODE: Int = 100
    var fusedLocationProviderClient: FusedLocationProviderClient? = null
    var handler: Handler? = null
    var refreshTime: Long = 5000 //5000ms = 5s

    var runnable: Runnable? = null

    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(
            layoutInflater
        )
        setContentView(binding!!.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        //to make nav bar transparent at the top and bottom of the screen
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        //handler class runs the same thing multiple times like a loop
        handler = Handler()
        handler!!.postDelayed(Runnable {
            handler!!.postDelayed(runnable!!, refreshTime)
            checkLocationPermission()
        }.also { runnable = it }, refreshTime)
    }

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
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
            //getLocation
            userLocation
            mMap!!.isMyLocationEnabled = true
            mMap!!.uiSettings.isCompassEnabled = true
            mMap!!.uiSettings.isZoomControlsEnabled = true
        } else {
            requestForPermissions()
        }
    }

    private val userLocation: Unit
        get() {
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

            val task = fusedLocationProviderClient!!.lastLocation
            task.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val longi = location.longitude

                    val userLocation = LatLng(lat, longi)

                    deviceId = getDeviceId(this)
                    val database = Firebase.database
                    val myRef = database.getReference("locations").child(deviceId)

                    myRef.child("latitude").setValue("${location.latitude}")
                    myRef.child("longitude").setValue("${location.longitude}")

                    mMap!!.moveCamera(CameraUpdateFactory.newLatLng(userLocation))

                    //mMap.animateCamera(CameraUpdateFactory.zoomTo(12f));
                    if (mMap != null) {
                        mMap!!.clear()
                        mMap!!.addMarker(
                            MarkerOptions().position(userLocation).title("$lat , $longi")
                                .icon(setIcon(this@MapsActivity, drawable.baseline_location_on_24))
                        )
                    }

                    //Log.i("XOXO", "$lat $longi")
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
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                userLocation
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap!!.uiSettings.isMyLocationButtonEnabled = true

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
        mMap!!.isMyLocationEnabled = true
    }

    //custom function to modify our custom map marker
    fun setIcon(context: Activity?, drawableID: Int): BitmapDescriptor {
        val drawable = ActivityCompat.getDrawable(context!!, drawableID)
        drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

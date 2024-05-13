package com.example.poseexercise.views.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.poseexercise.R
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.poseexercise.backgroundlocationtracking.LocationService
import com.example.poseexercise.backgroundlocationtracking.ui.theme.BackgroundLocationTrackingTheme
import com.example.poseexercise.databinding.ActivityMainBinding
import np.com.susanthapa.curved_bottom_navigation.CbnMenuItem

/**
 * Main Activity and entry point for the app.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        increaseNotificationVolume()

        // Get the navigation host fragment from this Activity
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        // Instantiate the navController using the NavHostFragment
        navController = navHostFragment.navController

        val menuItems = arrayOf(
            CbnMenuItem(
                R.drawable.home, // the icon
                R.drawable.avd_home, // the AVD that will be shown in FAB
                R.id.homeFragment // Jetpack Navigation
            ),
            CbnMenuItem(
                R.drawable.detect,
                R.drawable.avd_detect,
                R.id.detectFragment
            ),
            /*
            CbnMenuItem(
                R.drawable.plan,
                R.drawable.avd_plan,
                R.id.planStepOneFragment
            ),

             */
            CbnMenuItem(
                R.drawable.profile,
                R.drawable.avd_profile,
                R.id.profileFragment
            )
        )
        binding.navView.setMenuItems(menuItems, 0)
        binding.navView.setupWithNavController(navController)
//
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            0
        )
//        setContent {
//            BackgroundLocationTrackingTheme {
//                Column(
//                    modifier = Modifier.fillMaxSize()
//                ) {
//                    Button(onClick = {
//                        Intent(applicationContext, LocationService::class.java).apply {
//                            action = LocationService.ACTION_START
//                            startService(this)
//                        }
//                    }) {
//                        Text(text = "Start")
//                    }
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Button(onClick = {
//                        Intent(applicationContext, LocationService::class.java).apply {
//                            action = LocationService.ACTION_STOP
//                            startService(this)
//                        }
//                    }) {
//                        Text(text = "Stop")
//                    }
//                }
//            }
//        }
    }

    /**
     * Enables back button support. Simply navigates one element up on the stack.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        @kotlin.jvm.JvmField
        var minimum_confidence_tf_od_api: Float? = 0.3f
        var workoutResultData: String? = null
        var workoutTimer: String? = null
    }

    /**
     * This method is used to increase the notification sound volume to max
     */
    private fun increaseNotificationVolume() {
        // Increase the volume to max.
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            0
        )
    }
}


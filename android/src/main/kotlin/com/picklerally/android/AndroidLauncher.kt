package com.picklerally.android

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.firebase.FirebaseApp
import com.picklerally.core.PickleRallyGame

class AndroidLauncher : AndroidApplication() {
    private lateinit var nearbyService: NearbyNetworkService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
        }
        nearbyService = NearbyNetworkService(
            context = this,
            hasPermissions = ::hasNearbyPermissions,
            requestPermissions = ::requestNearbyPermissions
        )
        val networkService = runCatching {
            if (FirebaseApp.getApps(this).isNotEmpty()) FirebaseNetworkService() else LocalStubNetworkService()
        }.getOrElse { LocalStubNetworkService() }

        requestNearbyPermissions()
        initialize(
            PickleRallyGame(
                networkService = networkService,
                nearbyService = nearbyService
            ),
            config
        )
    }

    private fun requiredNearbyPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            needed += "android.permission.NEARBY_WIFI_DEVICES"
        }
        if (Build.VERSION.SDK_INT >= 31) {
            needed += "android.permission.BLUETOOTH_SCAN"
            needed += "android.permission.BLUETOOTH_ADVERTISE"
            needed += "android.permission.BLUETOOTH_CONNECT"
        }
        if (Build.VERSION.SDK_INT <= 32) {
            needed += "android.permission.ACCESS_FINE_LOCATION"
        }
        return needed
    }

    private fun hasNearbyPermissions(): Boolean =
        requiredNearbyPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Runtime permissions Google Nearby Connections needs for Nearby 1v1 on this Android version. */
    private fun requestNearbyPermissions() {
        val missing = requiredNearbyPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), NEARBY_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NEARBY_PERMISSION_REQUEST && ::nearbyService.isInitialized) {
            nearbyService.onPermissionsUpdated(hasNearbyPermissions())
        }
    }

    private companion object {
        const val NEARBY_PERMISSION_REQUEST = 4821
    }
}

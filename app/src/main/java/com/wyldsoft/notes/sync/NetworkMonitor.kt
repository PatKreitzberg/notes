// app/src/main/java/com/wyldsoft/notes/sync/NetworkMonitor.kt
package com.wyldsoft.notes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network status information
 */
data class NetworkStatus(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false
)

/**
 * Monitors network connectivity
 */
class NetworkMonitor(private val context: Context) {

    private val _networkStatus = MutableStateFlow(NetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    // For convenience
    val isConnected: Boolean
        get() = _networkStatus.value.isConnected

    val isWifi: Boolean
        get() = _networkStatus.value.isWifi

    /**
     * Check if we can sync based on settings
     */
    fun canSync(syncOnlyOnWifi: Boolean): Boolean {
        return isConnected && (!syncOnlyOnWifi || isWifi)
    }

    init {
        setupNetworkCallback()
        updateNetworkStatus() // Initial status
    }

    private fun setupNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus()
            }

            override fun onLost(network: Network) {
                updateNetworkStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateNetworkStatus()
            }
        }

        // Register for all network changes
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun updateNetworkStatus() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var isConnected = false
        var isWifi = false
        var isCellular = false

        connectivityManager.activeNetwork?.let { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null) {
                isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        }

        _networkStatus.value = NetworkStatus(
            isConnected = isConnected,
            isWifi = isWifi,
            isCellular = isCellular
        )
    }
}
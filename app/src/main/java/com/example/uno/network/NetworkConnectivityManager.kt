package com.example.uno.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Network Connectivity Manager for UNO Royale.
 * Enforces strict validated internet connectivity across the entire application.
 *
 * Checks:
 * - NetworkCapabilities.NET_CAPABILITY_INTERNET
 * - NetworkCapabilities.NET_CAPABILITY_VALIDATED
 *
 * Supported Transports:
 * - Wi-Fi (TRANSPORT_WIFI)
 * - Mobile Data (TRANSPORT_CELLULAR)
 * - Ethernet (TRANSPORT_ETHERNET)
 *
 * Exposes observable connectivity state via StateFlow and supports safe lifecycle registration.
 */
class NetworkConnectivityManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkValidatedInternetSync())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile
    private var isCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isValidated = isNetworkValidated(capabilities)
            Log.d(TAG, "Network onAvailable: validated=$isValidated")
            _isOnline.value = isValidated
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isValidated = isNetworkValidated(networkCapabilities)
            Log.d(TAG, "Network onCapabilitiesChanged: validated=$isValidated")
            _isOnline.value = isValidated
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network onLost: re-evaluating connectivity...")
            val isStillOnline = checkValidatedInternetSync()
            _isOnline.value = isStillOnline
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network onUnavailable: offline")
            _isOnline.value = false
        }
    }

    init {
        startMonitoring()
    }

    /**
     * Registers the Android NetworkCallback safely according to application lifecycle.
     */
    @Synchronized
    fun startMonitoring() {
        if (isCallbackRegistered) return

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)
            isCallbackRegistered = true
            Log.i(TAG, "NetworkCallback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback: ${e.message}", e)
        }

        checkConnectivity()
    }

    /**
     * Unregisters the Android NetworkCallback safely according to application lifecycle.
     */
    @Synchronized
    fun stopMonitoring() {
        if (!isCallbackRegistered) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isCallbackRegistered = false
            Log.i(TAG, "NetworkCallback unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister NetworkCallback: ${e.message}", e)
        }
    }

    /**
     * Performs a real synchronous check of active network capabilities.
     * Updates and returns the current connectivity state.
     */
    fun checkConnectivity(): Boolean {
        val online = checkValidatedInternetSync()
        _isOnline.value = online
        return online
    }

    /**
     * Internal validator that strictly checks:
     * 1. NET_CAPABILITY_INTERNET
     * 2. NET_CAPABILITY_VALIDATED (ensures real internet route, not just captive portal or dead link)
     * 3. Valid transport: Wi-Fi, Mobile Data, or Ethernet.
     */
    private fun isNetworkValidated(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) return false

        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val hasSupportedTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return hasInternetCapability && isValidated && hasSupportedTransport
    }

    private fun checkValidatedInternetSync(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return isNetworkValidated(capabilities)
    }

    companion object {
        private const val TAG = "NetworkConnectivity"

        @Volatile
        private var INSTANCE: NetworkConnectivityManager? = null

        fun getInstance(context: Context): NetworkConnectivityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkConnectivityManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun getInstanceOrNull(): NetworkConnectivityManager? = INSTANCE
    }
}

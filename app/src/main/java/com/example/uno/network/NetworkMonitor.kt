package com.example.uno.network

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Backward-compatible wrapper around [NetworkConnectivityManager].
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager = NetworkConnectivityManager.getInstance(context)

    val isConnected: StateFlow<Boolean> = connectivityManager.isOnline

    fun checkConnection(): Boolean {
        return connectivityManager.checkConnectivity()
    }
}

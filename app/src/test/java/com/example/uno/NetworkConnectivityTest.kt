package com.example.uno

import org.junit.Assert.*
import org.junit.Test

class NetworkConnectivityTest {

    // Simulates the validation algorithm in NetworkConnectivityManager
    private fun validateCapabilities(
        hasInternet: Boolean,
        isValidated: Boolean,
        isWifi: Boolean,
        isCellular: Boolean,
        isEthernet: Boolean
    ): Boolean {
        val hasSupportedTransport = isWifi || isCellular || isEthernet
        return hasInternet && isValidated && hasSupportedTransport
    }

    @Test
    fun wifiWithValidatedInternetReturnsTrue() {
        val online = validateCapabilities(
            hasInternet = true,
            isValidated = true,
            isWifi = true,
            isCellular = false,
            isEthernet = false
        )
        assertTrue(online)
    }

    @Test
    fun mobileDataWithValidatedInternetReturnsTrue() {
        val online = validateCapabilities(
            hasInternet = true,
            isValidated = true,
            isWifi = false,
            isCellular = true,
            isEthernet = false
        )
        assertTrue(online)
    }

    @Test
    fun ethernetWithValidatedInternetReturnsTrue() {
        val online = validateCapabilities(
            hasInternet = true,
            isValidated = true,
            isWifi = false,
            isCellular = false,
            isEthernet = true
        )
        assertTrue(online)
    }

    @Test
    fun wifiWithoutValidationReturnsFalse() {
        // e.g. Connected to Wi-Fi router or captive portal without actual internet route
        val online = validateCapabilities(
            hasInternet = true,
            isValidated = false,
            isWifi = true,
            isCellular = false,
            isEthernet = false
        )
        assertFalse(online)
    }

    @Test
    fun cellularWithoutValidationReturnsFalse() {
        val online = validateCapabilities(
            hasInternet = true,
            isValidated = false,
            isWifi = false,
            isCellular = true,
            isEthernet = false
        )
        assertFalse(online)
    }

    @Test
    fun noInternetCapabilityReturnsFalse() {
        // Local network only
        val online = validateCapabilities(
            hasInternet = false,
            isValidated = false,
            isWifi = true,
            isCellular = false,
            isEthernet = false
        )
        assertFalse(online)
    }

    @Test
    fun airplaneModeAllTransportsDisabledReturnsFalse() {
        val online = validateCapabilities(
            hasInternet = false,
            isValidated = false,
            isWifi = false,
            isCellular = false,
            isEthernet = false
        )
        assertFalse(online)
    }
}

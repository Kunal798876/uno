package com.example.uno

import android.app.Application

/**
 * Application class for UNO Royale.
 * Prevents Android AdServices MeasurementManager/TopicsManager null-binding errors on devices
 * or emulators without Play Services AdServices while initializing Unity Ads at startup.
 */
class UnoRoyaleApplication : Application() {

    override fun getSystemServiceName(serviceClass: Class<*>): String? {
        if (serviceClass.name.startsWith("android.adservices.")) {
            return null
        }
        return super.getSystemServiceName(serviceClass)
    }

    override fun getSystemService(name: String): Any? {
        if (name.contains("adservices", ignoreCase = true) || name == "measurement") {
            return null
        }
        return super.getSystemService(name)
    }
}

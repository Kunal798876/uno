package com.example.uno.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

object AdManager {
    private const val TAG = "StartIoAdManager"

    // Production Start.io Android App ID
    const val STARTIO_APP_ID = "210911961"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isSdkReady = false

    private var rewardedAd: StartAppAd? = null

    @Volatile
    private var isRewardedLoaded = false

    @Volatile
    private var isRewardedLoading = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes Start.io SDK once with the production App ID.
     */
    fun initialize(activity: Activity) {
        if (isInitialized) {
            Log.d(TAG, "Start.io SDK already initialized")
            return
        }

        try {
            Log.i(TAG, "START.IO SDK INITIALIZING")

            StartAppSDK.initParams(activity, STARTIO_APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    isSdkReady = true
                    Log.i(TAG, "START.IO SDK READY")
                    preloadRewardedVideo(activity.applicationContext)
                }
                .init()

            StartAppSDK.enableReturnAds(false)
            isInitialized = true
        } catch (e: Exception) {
            isInitialized = false
            isSdkReady = false
            Log.e(TAG, "START.IO INITIALIZATION FAILED: ${e.message}", e)
        }
    }

    /**
     * Preloads a Start.io rewarded video ad.
     * Prevents multiple simultaneous ad requests and verifies SDK readiness.
     */
    fun preloadRewardedVideo(context: Context) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot preload rewarded video: Start.io SDK is not initialized yet")
            return
        }
        if (isRewardedLoading) {
            Log.d(TAG, "Start.io rewarded video is already being requested")
            return
        }
        if (isRewardedLoaded && rewardedAd?.isReady == true) {
            Log.d(TAG, "Start.io rewarded video is already loaded and ready to display")
            return
        }

        try {
            isRewardedLoading = true
            Log.i(TAG, "START.IO REWARDED VIDEO REQUESTED")

            val ad = StartAppAd(context)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(adInstance: Ad) {
                    rewardedAd = ad
                    isRewardedLoaded = true
                    isRewardedLoading = false
                    Log.i(TAG, "START.IO REWARDED VIDEO LOADED")
                }

                override fun onFailedToReceiveAd(adInstance: Ad?) {
                    rewardedAd = null
                    isRewardedLoaded = false
                    isRewardedLoading = false
                    val errorMsg = adInstance?.errorMessage ?: "NO FILL"
                    Log.w(TAG, "START.IO REWARDED VIDEO FAILED: $errorMsg")
                }
            })
        } catch (e: Exception) {
            isRewardedLoading = false
            Log.e(TAG, "START.IO REWARDED VIDEO FAILED: ${e.message}", e)
        }
    }

    /**
     * Displays the preloaded Start.io rewarded video ad.
     * When completed or closed, invokes [onAdClosed] to return to the Home screen.
     * No rewards, points, or balances are granted.
     */
    fun showRewardedVideo(activity: Activity, onAdClosed: () -> Unit = {}) {
        val currentAd = rewardedAd
        if (currentAd != null && isRewardedLoaded && currentAd.isReady) {
            // Invalidate current reference so next ad can be queued
            rewardedAd = null
            isRewardedLoaded = false

            currentAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    Log.i(TAG, "Start.io rewarded video playback completed")
                }
            })

            val displayed = currentAd.showAd(object : AdDisplayListener {
                override fun adDisplayed(ad: Ad?) {
                    Log.i(TAG, "START.IO REWARDED VIDEO DISPLAYED")
                }

                override fun adHidden(ad: Ad?) {
                    Log.i(TAG, "START.IO REWARDED VIDEO CLOSED")
                    preloadRewardedVideo(activity.applicationContext)
                    onAdClosed()
                }

                override fun adClicked(ad: Ad?) {
                    Log.i(TAG, "Start.io rewarded video clicked")
                }

                override fun adNotDisplayed(ad: Ad?) {
                    Log.w(TAG, "START.IO REWARDED VIDEO FAILED: adNotDisplayed callback")
                    preloadRewardedVideo(activity.applicationContext)
                    onAdClosed()
                }
            })

            if (!displayed) {
                Log.w(TAG, "START.IO REWARDED VIDEO FAILED: showAd returned false")
                preloadRewardedVideo(activity.applicationContext)
                onAdClosed()
            }
        } else {
            Log.w(TAG, "START.IO REWARDED VIDEO FAILED: Ad not ready or still loading")
            preloadRewardedVideo(activity.applicationContext)
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Advertisement is loading, please try again in a moment",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onAdClosed()
        }
    }
}

/**
 * Start.io Production Banner Ad Composable.
 * Embeds official native Start.io Banner view via AndroidView with lifecycle and callbacks.
 * No fake banners or fabricated success messages are displayed.
 */
@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                Log.i("StartIoAdManager", "START.IO BANNER REQUESTED")
                val banner = Banner(context, object : BannerListener {
                    override fun onReceiveAd(bannerView: View?) {
                        Log.i("StartIoAdManager", "START.IO BANNER LOADED")
                    }

                    override fun onFailedToReceiveAd(bannerView: View?) {
                        Log.w("StartIoAdManager", "START.IO BANNER FAILED")
                    }

                    override fun onClick(bannerView: View?) {
                        Log.i("StartIoAdManager", "Start.io banner clicked")
                    }

                    override fun onImpression(bannerView: View?) {
                        Log.i("StartIoAdManager", "Start.io banner impression recorded")
                    }
                })
                banner
            }
        )
    }
}

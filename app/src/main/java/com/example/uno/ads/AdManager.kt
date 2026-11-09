package com.example.uno.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.uno.ui.theme.UnoGold
import com.example.uno.ui.theme.UnoYellow
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

object AdManager {
    private const val TAG = "StartIoAdManager"

    // Start.io Publisher / Account ID from authorized app-ads.txt
    const val STARTIO_APP_ID = "143270238"

    private var isInitialized = false
    private var rewardedAd: StartAppAd? = null
    private var isRewardedLoaded = false
    private var isRewardedLoading = false

    fun initialize(activity: Activity) {
        if (isInitialized) return
        try {
            // Modern Start.io SDK 5.4.0 initialization
            StartAppSDK.initParams(activity, STARTIO_APP_ID)
                .setReturnAdsEnabled(false)
                .init()

            // Enable test ads for testing/emulator environment to avoid NO FILL
            StartAppSDK.setTestAdsEnabled(true)
            StartAppSDK.enableReturnAds(false)

            isInitialized = true
            Log.d(TAG, "Start.io SDK initialized with App ID $STARTIO_APP_ID (Test mode enabled)")
            preloadRewardedAd(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Start.io SDK: ${e.message}", e)
        }
    }

    fun preloadRewardedAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (isRewardedLoaded || isRewardedLoading) {
            onLoaded?.invoke()
            return
        }

        try {
            isRewardedLoading = true
            val ad = StartAppAd(context)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(adInstance: Ad) {
                    rewardedAd = ad
                    isRewardedLoaded = true
                    isRewardedLoading = false
                    Log.d(TAG, "Start.io Rewarded Video loaded successfully")
                    onLoaded?.invoke()
                }

                override fun onFailedToReceiveAd(adInstance: Ad?) {
                    rewardedAd = null
                    isRewardedLoaded = false
                    isRewardedLoading = false
                    Log.i(TAG, "Start.io Rewarded Video unfill/offline: ${adInstance?.errorMessage ?: "NO FILL"}")
                }
            })
        } catch (e: Exception) {
            isRewardedLoading = false
            Log.e(TAG, "Exception preloading Start.io Rewarded Video: ${e.message}")
        }
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFallbackRequired: () -> Unit
    ) {
        val currentAd = rewardedAd
        if (currentAd != null && isRewardedLoaded) {
            currentAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    Log.d(TAG, "Start.io Rewarded video completed! Rewarding user.")
                    onRewardEarned()
                }
            })

            val shown = currentAd.showAd()
            isRewardedLoaded = false
            rewardedAd = null
            preloadRewardedAd(activity)

            if (!shown) {
                Log.i(TAG, "StartAppAd.showAd() returned false, triggering interactive reward fallback.")
                onFallbackRequired()
            }
        } else {
            // Ad not filled / offline: immediately trigger interactive fallback so user experience is uninterrupted
            preloadRewardedAd(activity)
            onFallbackRequired()
        }
    }
}

/**
 * Start.io Banner Ad Composable.
 * Embeds native Start.io Banner view with listener and graceful dark-themed placeholder when no fill.
 */
@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier
) {
    var bannerLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { ctx ->
                Banner(ctx, object : BannerListener {
                    override fun onReceiveAd(bannerView: View?) {
                        bannerLoaded = true
                        Log.d("StartIoBanner", "Start.io Banner loaded successfully")
                    }

                    override fun onFailedToReceiveAd(bannerView: View?) {
                        bannerLoaded = false
                        Log.i("StartIoBanner", "Start.io Banner not filled; using fallback presentation")
                    }

                    override fun onClick(bannerView: View?) {}
                    override fun onImpression(bannerView: View?) {}
                }).apply {
                    loadAd()
                }
            }
        )

        // Polished placeholder banner when ad is loading or offline / no-fill
        if (!bannerLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .border(1.dp, UnoGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(UnoYellow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "START.IO",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Uno Royale: 2-Player Duels • AI Arcade",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

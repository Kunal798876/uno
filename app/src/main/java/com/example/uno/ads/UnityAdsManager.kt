package com.example.uno.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.ads.*
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Unity Ads Production Manager for UNO Royale.
 * Exclusively manages Unity Ads native Android SDK with real production inventory (TEST_MODE = false).
 */
object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    // Unity Production Configuration
    const val UNITY_GAME_ID = "800430705"
    const val PLACEMENT_BANNER = "BP_Banner_Android"
    const val PLACEMENT_REWARDED = "BP_Rewarded_Android"
    const val PLACEMENT_INTERSTITIAL = "BP_Interstitial_Android"

    // Real production advertising strictly enabled (no test ads or demo placements)
    const val TEST_MODE = false

    @Volatile
    private var isInitializing = false

    // Observable SDK readiness state
    private val _isSdkReadyState = MutableStateFlow(false)
    val isSdkReadyState: StateFlow<Boolean> = _isSdkReadyState.asStateFlow()

    val isSdkReady: Boolean
        get() = _isSdkReadyState.value

    @Volatile
    private var isRewardedLoaded = false

    @Volatile
    private var isRewardedLoading = false

    @Volatile
    private var isInterstitialLoaded = false

    @Volatile
    private var isInterstitialLoading = false

    private var rewardedRetryCount = 0
    private const val INITIAL_RETRY_DELAY_MS = 5000L
    private const val MAX_RETRY_DELAY_MS = 60000L
    private var rewardedRetryRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes Unity Ads exactly once using Android Game ID 800430705 and TEST_MODE = false.
     */
    fun initialize(
        context: Context,
        onComplete: (() -> Unit)? = null
    ) {
        if (isSdkReady) {
            Log.d(TAG, "Unity Ads already initialized and ready")
            onComplete?.invoke()
            return
        }

        if (isInitializing) {
            Log.d(TAG, "Unity Ads initialization already in progress")
            return
        }

        try {
            isInitializing = true
            Log.i(TAG, "UNITY SDK INITIALIZING")

            UnityAds.initialize(
                context.applicationContext,
                UNITY_GAME_ID,
                TEST_MODE,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isInitializing = false
                        _isSdkReadyState.value = true
                        Log.i(TAG, "UNITY SDK INITIALIZED")

                        // Preload rewarded video and interstitial for production readiness
                        loadRewardedVideo()
                        loadInterstitial()

                        mainHandler.post { onComplete?.invoke() }
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String
                    ) {
                        isInitializing = false
                        _isSdkReadyState.value = false
                        Log.e(TAG, "UNITY SDK INITIALIZATION FAILED: $error - $message")
                    }
                }
            )
        } catch (e: Exception) {
            isInitializing = false
            _isSdkReadyState.value = false
            Log.e(TAG, "UNITY SDK INITIALIZATION FAILED: ${e.message}", e)
        }
    }

    /**
     * Preloads the Rewarded Video placement (BP_Rewarded_Android).
     */
    fun loadRewardedVideo() {
        if (!isSdkReady) {
            Log.w(TAG, "Cannot request rewarded ad: Unity SDK not ready yet")
            return
        }
        if (isRewardedLoading) {
            Log.d(TAG, "Unity rewarded video is already being requested")
            return
        }
        if (isRewardedLoaded) {
            Log.d(TAG, "Unity rewarded video is already loaded and ready")
            return
        }

        try {
            isRewardedLoading = true
            Log.i(TAG, "UNITY REWARDED REQUESTED")

            UnityAds.load(PLACEMENT_REWARDED, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isRewardedLoaded = true
                    isRewardedLoading = false
                    rewardedRetryCount = 0
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    Log.i(TAG, "UNITY REWARDED LOADED")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    isRewardedLoaded = false
                    isRewardedLoading = false
                    Log.w(TAG, "UNITY REWARDED FAILED: $placementId - $error: $message")

                    // Background retry with exponential backoff for inventory availability
                    val delayMs = (INITIAL_RETRY_DELAY_MS * (1 shl rewardedRetryCount.coerceAtMost(4)))
                        .coerceAtMost(MAX_RETRY_DELAY_MS)
                    rewardedRetryCount++
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        Log.d(TAG, "Retrying Unity rewarded video load after backoff (${delayMs}ms, attempt $rewardedRetryCount)")
                        loadRewardedVideo()
                    }
                    rewardedRetryRunnable = runnable
                    mainHandler.postDelayed(runnable, delayMs)
                }
            })
        } catch (e: Exception) {
            isRewardedLoading = false
            Log.e(TAG, "UNITY REWARDED FAILED: ${e.message}", e)
        }
    }

    /**
     * Shows the preloaded Rewarded Video ad (BP_Rewarded_Android).
     * The gift icon is solely a button to watch an ad — no coins, stars, points, or balances are granted.
     */
    fun showRewardedVideo(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (!isSdkReady) {
            Log.w(TAG, "UNITY REWARDED FAILED: SDK not initialized")
            initialize(activity) {
                showRewardedVideo(activity, onAdClosed)
            }
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Advertisement is initializing, please try again in a moment",
                    Toast.LENGTH_SHORT
                ).show()
                onAdClosed()
            }
            return
        }

        if (isRewardedLoaded) {
            isRewardedLoaded = false
            Log.i(TAG, "UNITY REWARDED DISPLAYED")

            UnityAds.show(
                activity,
                PLACEMENT_REWARDED,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.i(TAG, "Unity rewarded video playback started: $placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.i(TAG, "Unity rewarded video clicked: $placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.i(TAG, "UNITY REWARDED CLOSED")
                        // Preload next ad immediately
                        loadRewardedVideo()
                        // Return to Home screen without granting any rewards or balance updates
                        mainHandler.post { onAdClosed() }
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.w(TAG, "UNITY REWARDED FAILED: $placementId - $error: $message")
                        loadRewardedVideo()
                        mainHandler.post { onAdClosed() }
                    }
                }
            )
        } else {
            Log.w(TAG, "UNITY REWARDED FAILED: Ad not ready or still loading")
            rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            loadRewardedVideo()
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Advertisement is loading, please try again in a moment",
                    Toast.LENGTH_SHORT
                ).show()
                onAdClosed()
            }
        }
    }

    /**
     * Preloads the Interstitial placement (BP_Interstitial_Android).
     */
    fun loadInterstitial() {
        if (!isSdkReady || isInterstitialLoading || isInterstitialLoaded) return

        try {
            isInterstitialLoading = true
            Log.d(TAG, "Loading Interstitial ($PLACEMENT_INTERSTITIAL)...")

            UnityAds.load(PLACEMENT_INTERSTITIAL, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isInterstitialLoaded = true
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial loaded: $placementId")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    isInterstitialLoaded = false
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial failed to load: $placementId - $error: $message")
                }
            })
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.e(TAG, "Interstitial load exception: ${e.message}", e)
        }
    }

    /**
     * Shows the Interstitial ad if loaded.
     * Maintained for configuration/future use; never displayed automatically.
     */
    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (isInterstitialLoaded) {
            isInterstitialLoaded = false
            UnityAds.show(
                activity,
                PLACEMENT_INTERSTITIAL,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {}
                    override fun onUnityAdsShowClick(placementId: String) {}
                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        loadInterstitial()
                        mainHandler.post { onAdClosed() }
                    }
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        loadInterstitial()
                        mainHandler.post { onAdClosed() }
                    }
                }
            )
        } else {
            loadInterstitial()
            onAdClosed()
        }
    }

    /**
     * Creates and loads a real Unity BannerView instance for BP_Banner_Android.
     * Returns null if Unity SDK is not yet initialized.
     */
    fun createBannerView(
        activity: Activity,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ): BannerView? {
        if (!UnityAds.isInitialized) {
            Log.w(TAG, "Cannot create banner: Unity SDK is not initialized yet")
            onFailed?.invoke("SDK_NOT_INITIALIZED")
            return null
        }

        Log.i(TAG, "UNITY BANNER REQUESTED")
        val bannerSize = UnityBannerSize(320, 50)
        val bannerView = BannerView(activity, PLACEMENT_BANNER, bannerSize)

        bannerView.listener = object : BannerView.IListener {
            override fun onBannerLoaded(banner: BannerView) {
                Log.i(TAG, "UNITY BANNER LOADED")
                onLoaded?.invoke()
            }

            override fun onBannerFailedToLoad(banner: BannerView, errorInfo: BannerErrorInfo) {
                Log.w(TAG, "UNITY BANNER FAILED: ${errorInfo.errorMessage}")
                onFailed?.invoke(errorInfo.errorMessage)
            }

            override fun onBannerClick(banner: BannerView) {
                Log.i(TAG, "Unity banner clicked: ${banner.placementId}")
            }

            override fun onBannerShown(banner: BannerView) {
                Log.i(TAG, "Unity banner impression recorded: ${banner.placementId}")
            }

            override fun onBannerLeftApplication(banner: BannerView) {
                Log.i(TAG, "Unity banner left application: ${banner.placementId}")
            }
        }

        bannerView.load()
        return bannerView
    }
}

/**
 * Jetpack Compose Unity Ads Banner Composable.
 * Strictly waits for actual SDK initialization callback confirmation before creating BannerView.
 * Ensures exactly one BannerView instance is created per screen lifecycle (remember(activity)).
 * Manages BannerView lifecycle with DisposableEffect to prevent duplicate instances or memory leaks.
 * Only displays a banner when Unity actually supplies an advertisement (no fake banners or placeholders).
 */
@Composable
fun UnityBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isSdkReady by UnityAdsManager.isSdkReadyState.collectAsState()

    // Strictly wait for actual SDK initialization callback confirmation before creating BannerView
    if (activity != null && isSdkReady && UnityAds.isInitialized) {
        var isAdLoaded by remember { mutableStateOf(false) }

        // Remember single BannerView across recompositions for the screen's lifecycle
        val bannerView = remember(activity) {
            UnityAdsManager.createBannerView(
                activity = activity,
                onLoaded = { isAdLoaded = true },
                onFailed = { isAdLoaded = false }
            )
        }

        DisposableEffect(bannerView) {
            onDispose {
                bannerView?.destroy()
            }
        }

        if (bannerView != null) {
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
                    factory = {
                        bannerView.parent?.let { parent ->
                            (parent as? android.view.ViewGroup)?.removeView(bannerView)
                        }
                        bannerView
                    }
                )
            }
        }
    }
}

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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.uno.network.NetworkConnectivityManager
import com.unity3d.ads.*
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Unity Ads Production Manager for UNO Royale.
 * Exclusively manages Unity Ads native Android SDK with real production inventory (TEST_MODE = false).
 * Enforces validated internet connectivity before requesting advertisements.
 *
 * Note on "Header bidding load invocation failed: adMarkup is missing; objectId is missing":
 * Placements prefixed with "BP_" (e.g. BP_Banner_Android, BP_Rewarded_Android) are configured
 * on the Unity Monetization dashboard as Bidding Placements (for mediation partners like LevelPlay/MAX/AdMob).
 * When accessed via the direct native Unity Ads SDK without mediation, Unity's internal SDK logs this warning
 * because standard direct requests do not contain bidding auction tokens. For native standalone integrations,
 * placements should be set as Standard/Waterfall on the Unity dashboard.
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
        get() = _isSdkReadyState.value && UnityAds.isInitialized

    @Volatile
    private var isRewardedLoaded = false

    @Volatile
    private var isRewardedLoading = false

    @Volatile
    private var isInterstitialLoaded = false

    @Volatile
    private var isInterstitialLoading = false

    @Volatile
    private var isAdShowing = false

    // Pending show request queue: if user clicks gift icon before SDK init or during load, auto-present when ready
    @Volatile
    private var isShowPendingWhenLoaded = false
    private var pendingShowActivity: WeakReference<Activity>? = null
    private var pendingOnAdClosed: (() -> Unit)? = null

    // Initialization retry
    private var initRetryCount = 0
    private const val MAX_INIT_RETRIES = 3
    private var initRetryRunnable: Runnable? = null

    // Rewarded load retry
    private var rewardedRetryCount = 0
    private const val INITIAL_RETRY_DELAY_MS = 5000L
    private const val MAX_RETRY_DELAY_MS = 60000L
    private var rewardedRetryRunnable: Runnable? = null

    // Controlled banner retry
    private var bannerRetryCount = 0
    private const val MAX_BANNER_RETRIES = 5
    private var bannerRetryRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes Unity Ads exactly once using Android Game ID 800430705 and TEST_MODE = false.
     * Enforces active validated internet connectivity.
     */
    fun initialize(
        context: Context,
        onComplete: (() -> Unit)? = null
    ) {
        val networkManager = NetworkConnectivityManager.getInstance(context)
        if (!networkManager.isOnline.value) {
            Log.w(TAG, "Cannot initialize Unity Ads: Device has no validated internet")
            return
        }

        if (UnityAds.isInitialized) {
            _isSdkReadyState.value = true
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
            Log.i(TAG, "UNITY_INIT_STARTED: Game ID $UNITY_GAME_ID, Test Mode $TEST_MODE")

            UnityAds.initialize(
                context.applicationContext,
                UNITY_GAME_ID,
                TEST_MODE,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isInitializing = false
                        initRetryCount = 0
                        initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                        _isSdkReadyState.value = true
                        Log.i(TAG, "UNITY_INIT_SUCCESS")

                        // Preload placements for production readiness if online
                        if (networkManager.isOnline.value) {
                            loadRewardedVideo()
                            loadInterstitial()
                        }

                        mainHandler.post { onComplete?.invoke() }
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String
                    ) {
                        isInitializing = false
                        _isSdkReadyState.value = false
                        Log.e(TAG, "UNITY_INIT_FAILED: $error - $message")

                        // If user was waiting for the ad to show, handle failure
                        if (isShowPendingWhenLoaded) {
                            if (initRetryCount >= MAX_INIT_RETRIES) {
                                isShowPendingWhenLoaded = false
                                val activity = pendingShowActivity?.get()
                                pendingShowActivity = null
                                pendingOnAdClosed = null

                                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                    mainHandler.post {
                                        Toast.makeText(
                                            activity,
                                            "Advertisement is currently unavailable. Please check connection and try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }

                        // Controlled initialization retry with backoff
                        if (initRetryCount < MAX_INIT_RETRIES && networkManager.isOnline.value) {
                            initRetryCount++
                            val delayMs = 5000L * initRetryCount
                            initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                            val runnable = Runnable {
                                Log.d(TAG, "Retrying Unity Ads initialization (attempt $initRetryCount)...")
                                initialize(context.applicationContext, onComplete)
                            }
                            initRetryRunnable = runnable
                            mainHandler.postDelayed(runnable, delayMs)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            isInitializing = false
            _isSdkReadyState.value = false
            Log.e(TAG, "UNITY_INIT_FAILED: ${e.message}", e)
        }
    }

    /**
     * Preloads the Rewarded Video placement (BP_Rewarded_Android).
     * Enforces active validated internet connectivity.
     */
    fun loadRewardedVideo() {
        val networkManager = NetworkConnectivityManager.getInstanceOrNull()
        if (networkManager != null && !networkManager.isOnline.value) {
            Log.w(TAG, "Cannot load rewarded video: Device is offline")
            return
        }

        if (!UnityAds.isInitialized) {
            Log.w(TAG, "Cannot request rewarded ad: Unity SDK not initialized yet")
            return
        }
        if (isRewardedLoading) {
            Log.d(TAG, "Unity rewarded video is already loading")
            return
        }
        if (isRewardedLoaded) {
            Log.d(TAG, "Unity rewarded video is already loaded and ready")
            return
        }

        try {
            isRewardedLoading = true
            Log.i(TAG, "UNITY_REWARDED_REQUESTED: $PLACEMENT_REWARDED")

            UnityAds.load(PLACEMENT_REWARDED, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isRewardedLoaded = true
                    isRewardedLoading = false
                    rewardedRetryCount = 0
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    Log.i(TAG, "UNITY_REWARDED_LOADED: $placementId")

                    // If user tapped gift icon while loading, automatically display now
                    if (isShowPendingWhenLoaded) {
                        isShowPendingWhenLoaded = false
                        val activity = pendingShowActivity?.get()
                        val callback = pendingOnAdClosed ?: {}
                        pendingShowActivity = null
                        pendingOnAdClosed = null

                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            mainHandler.post {
                                displayLoadedRewardedVideo(activity, callback)
                            }
                        }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    isRewardedLoaded = false
                    isRewardedLoading = false

                    if (error == UnityAds.UnityAdsLoadError.NO_FILL) {
                        Log.w(TAG, "UNITY_REWARDED_FAILED (NO_FILL): $placementId - $message")
                    } else {
                        Log.w(TAG, "UNITY_REWARDED_FAILED: $placementId - $error: $message")
                    }

                    // If user was waiting for the ad to load, notify that it is currently unavailable
                    if (isShowPendingWhenLoaded) {
                        isShowPendingWhenLoaded = false
                        val activity = pendingShowActivity?.get()
                        pendingShowActivity = null
                        pendingOnAdClosed = null

                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            mainHandler.post {
                                val userMessage = if (error == UnityAds.UnityAdsLoadError.NO_FILL) {
                                    "Advertisement is currently unavailable (no inventory). Please try again later."
                                } else {
                                    "Advertisement is currently unavailable. Please check connection and try again."
                                }
                                Toast.makeText(activity, userMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                        // Do NOT call onAdClosed() when ad has not actually been displayed!
                    }

                    // Controlled background retry with exponential backoff if online
                    val isOnline = NetworkConnectivityManager.getInstanceOrNull()?.isOnline?.value ?: false
                    if (isOnline) {
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
                }
            })
        } catch (e: Exception) {
            isRewardedLoading = false
            Log.e(TAG, "UNITY_REWARDED_FAILED: ${e.message}", e)
            if (isShowPendingWhenLoaded) {
                isShowPendingWhenLoaded = false
                pendingShowActivity?.get()?.let { act ->
                    mainHandler.post {
                        Toast.makeText(act, "Advertisement is currently unavailable. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                pendingShowActivity = null
                pendingOnAdClosed = null
            }
        }
    }

    /**
     * Handles gift icon tap with user-friendly loading & automatic presentation.
     * Enforces active validated internet connectivity.
     */
    fun showRewardedVideo(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot show rewarded video: Activity is finishing or destroyed")
            return
        }

        // Check internet connection
        val networkManager = NetworkConnectivityManager.getInstance(activity)
        if (!networkManager.isOnline.value) {
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Internet connection required to load advertisements",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (isAdShowing) {
            Log.d(TAG, "An advertisement is already displaying")
            return
        }

        // 1. If SDK is NOT initialized yet: retain request, show loading notice, and initialize
        if (!UnityAds.isInitialized) {
            Log.i(TAG, "UNITY_REWARDED_REQUESTED: SDK initializing, queuing auto-display upon readiness")
            isShowPendingWhenLoaded = true
            pendingShowActivity = WeakReference(activity)
            pendingOnAdClosed = onAdClosed

            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Loading advertisement...",
                    Toast.LENGTH_SHORT
                ).show()
            }

            initialize(activity) {
                loadRewardedVideo()
            }
            return
        }

        // 2. If rewarded ad is already loaded, show it immediately!
        if (isRewardedLoaded) {
            Log.i(TAG, "UNITY_REWARDED_REQUESTED: Ad already loaded, displaying immediately")
            displayLoadedRewardedVideo(activity, onAdClosed)
            return
        }

        // 3. If already loading, queue the request and show a loading notice
        if (isRewardedLoading) {
            Log.i(TAG, "UNITY_REWARDED_REQUESTED: Load in progress, queuing auto-display")
            isShowPendingWhenLoaded = true
            pendingShowActivity = WeakReference(activity)
            pendingOnAdClosed = onAdClosed
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Loading advertisement, opening in a moment...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // 4. No request in progress: start loading and automatically show when loaded
        Log.i(TAG, "UNITY_REWARDED_REQUESTED: Starting load with auto-display queued")
        isShowPendingWhenLoaded = true
        pendingShowActivity = WeakReference(activity)
        pendingOnAdClosed = onAdClosed
        mainHandler.post {
            Toast.makeText(
                activity,
                "Loading advertisement...",
                Toast.LENGTH_SHORT
            ).show()
        }
        loadRewardedVideo()
    }

    /**
     * Displays a loaded rewarded ad and listens for playback completion.
     */
    private fun displayLoadedRewardedVideo(activity: Activity, onAdClosed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot show rewarded video: Activity is invalid")
            return
        }

        if (isAdShowing) {
            Log.w(TAG, "Cannot show rewarded video: Another ad is currently showing")
            return
        }

        isRewardedLoaded = false
        isAdShowing = true
        Log.i(TAG, "UNITY_REWARDED_DISPLAYED: $PLACEMENT_REWARDED")

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
                    isAdShowing = false
                    Log.i(TAG, "UNITY_REWARDED_COMPLETED: $placementId - $state")
                    // Preload next ad immediately if online
                    loadRewardedVideo()
                    // Return to Home screen without granting any rewards or balance updates
                    mainHandler.post { onAdClosed() }
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isAdShowing = false
                    Log.e(TAG, "UNITY_REWARDED_SHOW_FAILURE: $placementId - $error: $message")
                    mainHandler.post {
                        Toast.makeText(activity, "Failed to display advertisement.", Toast.LENGTH_SHORT).show()
                    }
                    loadRewardedVideo()
                    mainHandler.post { onAdClosed() }
                }
            }
        )
    }

    /**
     * Preloads the Interstitial placement (BP_Interstitial_Android).
     */
    fun loadInterstitial() {
        val isOnline = NetworkConnectivityManager.getInstanceOrNull()?.isOnline?.value ?: false
        if (!isOnline) return

        if (!UnityAds.isInitialized || isInterstitialLoading || isInterstitialLoaded) return

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
     */
    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) {
        val isOnline = NetworkConnectivityManager.getInstance(activity).isOnline.value
        if (!isOnline) {
            onAdClosed()
            return
        }

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
     * Creates a real Unity BannerView instance for BP_Banner_Android.
     * Returns null if Unity SDK is not yet initialized or if offline.
     */
    fun createBannerView(
        activity: Activity,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ): BannerView? {
        val networkManager = NetworkConnectivityManager.getInstance(activity)
        if (!networkManager.isOnline.value) {
            Log.w(TAG, "Cannot create banner: Device has no validated internet")
            onFailed?.invoke("DEVICE_OFFLINE")
            return null
        }

        if (!UnityAds.isInitialized) {
            Log.w(TAG, "Cannot create banner: Unity SDK is not initialized yet")
            onFailed?.invoke("SDK_NOT_INITIALIZED")
            return null
        }

        Log.i(TAG, "UNITY_BANNER_REQUESTED: $PLACEMENT_BANNER")
        val bannerSize = UnityBannerSize(320, 50)
        val bannerView = BannerView(activity, PLACEMENT_BANNER, bannerSize)

        bannerView.listener = object : BannerView.IListener {
            override fun onBannerLoaded(banner: BannerView) {
                bannerRetryCount = 0
                bannerRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                Log.i(TAG, "UNITY_BANNER_LOADED: ${banner.placementId}")
                mainHandler.post { onLoaded?.invoke() }
            }

            override fun onBannerFailedToLoad(banner: BannerView, errorInfo: BannerErrorInfo) {
                Log.w(TAG, "UNITY_BANNER_FAILED: ${errorInfo.errorMessage} (code: ${errorInfo.errorCode})")
                mainHandler.post { onFailed?.invoke(errorInfo.errorMessage) }

                // Controlled retry for temporary failures with backoff if still online
                val isOnline = NetworkConnectivityManager.getInstance(activity).isOnline.value
                if (isOnline && bannerRetryCount < MAX_BANNER_RETRIES) {
                    val retryDelayMs = (INITIAL_RETRY_DELAY_MS * (1 shl bannerRetryCount)).coerceAtMost(30000L)
                    bannerRetryCount++
                    bannerRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        Log.d(TAG, "Retrying Unity banner load (attempt $bannerRetryCount, delay ${retryDelayMs}ms)...")
                        bannerView.load()
                    }
                    bannerRetryRunnable = runnable
                    mainHandler.postDelayed(runnable, retryDelayMs)
                }
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

        return bannerView
    }

    /**
     * Cleanly destroys the banner view and cancels any pending retries.
     */
    fun destroyBanner(bannerView: BannerView?) {
        bannerRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        try {
            bannerView?.parent?.let { parent ->
                (parent as? android.view.ViewGroup)?.removeView(bannerView)
            }
            bannerView?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying banner view: ${e.message}", e)
        }
    }

    /**
     * Resumes ad preloading when network connectivity returns.
     */
    fun onNetworkRestored(context: Context) {
        Log.i(TAG, "Network restored: resuming Unity Ads")
        if (!UnityAds.isInitialized) {
            initialize(context)
        } else {
            if (!isRewardedLoaded && !isRewardedLoading) {
                loadRewardedVideo()
            }
            if (!isInterstitialLoaded && !isInterstitialLoading) {
                loadInterstitial()
            }
        }
    }

    /**
     * Pauses ad retries when network connectivity is lost.
     */
    fun onNetworkLost() {
        Log.i(TAG, "Network lost: pausing ad retries")
        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        bannerRetryRunnable?.let { mainHandler.removeCallbacks(it) }
    }
}

/**
 * Jetpack Compose Unity Ads Banner Composable.
 * Strictly waits for actual SDK initialization callback confirmation and validated internet before creating BannerView.
 * Ensures exactly one BannerView instance is created per screen lifecycle (remember(activity)).
 * Attaches BannerView immediately to AndroidView hierarchy and triggers load() once attached.
 * Maintains a 0.dp height container until onBannerLoaded callback confirms filled ad inventory.
 * Cleans up and destroys BannerView on disposal to prevent leaks.
 */
@Composable
fun UnityBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val networkManager = remember(context) { NetworkConnectivityManager.getInstance(context) }
    val isOnline by networkManager.isOnline.collectAsState()
    val isSdkReady by UnityAdsManager.isSdkReadyState.collectAsState()

    // Strictly wait for validated internet and actual SDK initialization confirmation
    if (activity != null && isOnline && isSdkReady && UnityAds.isInitialized) {
        var isAdLoaded by remember { mutableStateOf(false) }

        // Remember single BannerView across recompositions for the screen's lifecycle
        val bannerView = remember(activity) {
            UnityAdsManager.createBannerView(
                activity = activity,
                onLoaded = { isAdLoaded = true },
                onFailed = { isAdLoaded = false }
            )
        }

        // Destroy the banner correctly when the composable leaves the screen or goes offline
        DisposableEffect(bannerView) {
            onDispose {
                UnityAdsManager.destroyBanner(bannerView)
                isAdLoaded = false
            }
        }

        if (bannerView != null) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(if (isAdLoaded) 50.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.wrapContentSize(),
                    factory = {
                        bannerView.parent?.let { parent ->
                            (parent as? android.view.ViewGroup)?.removeView(bannerView)
                        }
                        bannerView.load()
                        bannerView
                    }
                )
            }
        }
    }
}

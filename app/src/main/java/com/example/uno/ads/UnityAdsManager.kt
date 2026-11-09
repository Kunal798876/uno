package com.example.uno.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.uno.BuildConfig
import com.example.uno.network.NetworkConnectivityManager
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import com.unity3d.services.core.log.DeviceLog
import com.unity3d.services.core.log.DeviceLogLevel
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level environment configuration flag:
 * - `false` on Debug builds (`testMode = true`)
 * - `true` on Release builds (`testMode = false`)
 */
val isProductionMode: Boolean = !BuildConfig.DEBUG

/**
 * Dedicated Unity Ads Manager for UNO Royale.
 * Exclusively manages Unity Ads native Android SDK with Game ID "800430705".
 */
object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    // Unity Dashboard Game ID (never used as a Placement ID)
    const val UNITY_GAME_ID: String = "800430705"

    // Configured Dashboard Placements for Game ID 800430705 + Standard Fallbacks
    const val PLACEMENT_INTERSTITIAL: String = "BP_Interstitial_Android"
    const val PLACEMENT_REWARDED: String = "BP_Rewarded_Android"
    const val PLACEMENT_BANNER: String = "BP_Banner_Android"

    private val INTERSTITIAL_CANDIDATES = listOf(
        "BP_Interstitial_Android",
        "Interstitial_Android",
        "video"
    )
    private val REWARDED_CANDIDATES = listOf(
        "BP_Rewarded_Android",
        "Rewarded_Android",
        "rewardedVideo"
    )
    private val BANNER_CANDIDATES = listOf(
        "BP_Banner_Android",
        "Banner_Android",
        "banner"
    )

    @Volatile
    private var activeInterstitialPlacement: String = PLACEMENT_INTERSTITIAL

    @Volatile
    private var activeRewardedPlacement: String = PLACEMENT_REWARDED

    @Volatile
    private var activeBannerPlacement: String = PLACEMENT_BANNER

    // Automatically true for debug builds (Unity test ads), false for release builds (live ads)
    @Volatile
    var testMode: Boolean = BuildConfig.DEBUG
        private set

    val placementInterstitial: String
        get() = activeInterstitialPlacement

    val placementRewarded: String
        get() = activeRewardedPlacement

    val placementBanner: String
        get() = activeBannerPlacement

    // Thread-safe initialization state tracking
    private val isInitInProgress = AtomicBoolean(false)
    private val isInitCompleted = AtomicBoolean(false)
    private val pendingInitActions = ConcurrentLinkedQueue<() -> Unit>()

    private val _isSdkReadyState = MutableStateFlow(false)
    val isSdkReadyState: StateFlow<Boolean> = _isSdkReadyState.asStateFlow()

    val isSdkReady: Boolean
        get() = isInitCompleted.get() && UnityAds.isInitialized

    // Thread-safe ad loading & presentation state flags
    private val isRewardedLoading = AtomicBoolean(false)
    private val isRewardedLoaded = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isInterstitialLoaded = AtomicBoolean(false)
    private val isAdShowing = AtomicBoolean(false)

    // Queued user request when tapping the gift icon while SDK is initializing or loading
    @Volatile
    private var isRewardedShowPending = false
    private var pendingShowActivityRef: WeakReference<Activity>? = null
    private var pendingOnAdClosedCallback: (() -> Unit)? = null

    // Retry handlers and backoff parameters
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initRetryCount = 0
    private var rewardedRetryCount = 0
    private var interstitialRetryCount = 0
    private const val MAX_INIT_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 10000L
    private const val MAX_RETRY_DELAY_MS = 60000L

    private var initRetryRunnable: Runnable? = null
    private var rewardedRetryRunnable: Runnable? = null
    private var interstitialRetryRunnable: Runnable? = null

    /**
     * Suppresses internal Unity WebView `Sdk.logError` Log.e spam for unconfigured/unpublished
     * placements by downgrading `DeviceLog`'s internal ERROR level to `w` (warning) and setting
     * `DeviceLog.setLogLevel(0)`.
     */
    private fun configureUnitySdkLogging() {
        try {
            DeviceLog.setLogLevel(0)
            val field = DeviceLog::class.java.getDeclaredField("_deviceLogLevel")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as? HashMap<Any, DeviceLogLevel>
            if (map != null) {
                for (key in map.keys.toList()) {
                    if (key.toString() == "ERROR") {
                        map[key] = DeviceLogLevel("w")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unity DeviceLog configuration skipped: ${e.message}")
        }
    }

    /**
     * Thread-safe initialization entry point.
     * Calls `UnityAds.initialize(context, "800430705", testMode, initializationListener)`.
     */
    @Synchronized
    fun initialize(
        context: Context,
        testMode: Boolean = BuildConfig.DEBUG,
        onComplete: (() -> Unit)? = null
    ) {
        this.testMode = testMode
        configureUnitySdkLogging()

        val appContext = context.applicationContext
        val networkManager = NetworkConnectivityManager.getInstance(appContext)

        if (onComplete != null) {
            pendingInitActions.add(onComplete)
        }

        if (!networkManager.isOnline.value) {
            Log.w(TAG, "UNITY_INIT_SKIPPED: No validated internet connection available.")
            return
        }

        if (isInitCompleted.get() && UnityAds.isInitialized) {
            Log.d(TAG, "Unity Ads SDK is already initialized. Executing queued actions immediately.")
            _isSdkReadyState.value = true
            drainPendingInitActions()
            return
        }

        if (isInitInProgress.get()) {
            Log.d(TAG, "Unity Ads SDK initialization is already in progress. Waiting for callback.")
            return
        }

        try {
            isInitInProgress.set(true)
            Log.d(
                TAG,
                "UNITY_INIT_STARTED: gameId=$UNITY_GAME_ID, testMode=${this.testMode}"
            )

            val initializationListener = object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    configureUnitySdkLogging()
                    isInitInProgress.set(false)
                    isInitCompleted.set(true)
                    initRetryCount = 0
                    initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    _isSdkReadyState.value = true

                    Log.d(
                        TAG,
                        "UNITY_INIT_SUCCESS: SDK initialized (gameId=$UNITY_GAME_ID, testMode=${this@UnityAdsManager.testMode})"
                    )

                    // Preload Rewarded and Interstitial ads now that initialization is complete
                    loadRewardedVideo()
                    loadInterstitial()

                    // Drain any queued actions waiting on SDK initialization
                    drainPendingInitActions()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    isInitInProgress.set(false)
                    isInitCompleted.set(false)
                    _isSdkReadyState.value = false

                    Log.w(
                        TAG,
                        "UNITY_INIT_FAILED: error=${error?.name ?: "UNKNOWN"}, message=${message ?: "No message"}"
                    )

                    if (isRewardedShowPending && initRetryCount >= MAX_INIT_RETRIES) {
                        isRewardedShowPending = false
                        val activity = pendingShowActivityRef?.get()
                        pendingShowActivityRef = null
                        pendingOnAdClosedCallback = null
                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            mainHandler.post {
                                Toast.makeText(
                                    activity,
                                    "Advertisement failed to initialize. Please try again later.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    if (initRetryCount < MAX_INIT_RETRIES && networkManager.isOnline.value) {
                        initRetryCount++
                        val delayMs = INITIAL_RETRY_DELAY_MS * initRetryCount
                        Log.d(TAG, "Scheduling Unity SDK init retry #$initRetryCount in ${delayMs}ms")
                        initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            initialize(appContext, this@UnityAdsManager.testMode)
                        }
                        initRetryRunnable = runnable
                        mainHandler.postDelayed(runnable, delayMs)
                    }
                }
            }

            UnityAds.initialize(
                appContext,
                UNITY_GAME_ID,
                this.testMode,
                initializationListener
            )
            configureUnitySdkLogging()
        } catch (e: Exception) {
            isInitInProgress.set(false)
            isInitCompleted.set(false)
            _isSdkReadyState.value = false
            Log.w(TAG, "UNITY_INIT_EXCEPTION: Failed to invoke UnityAds.initialize: ${e.message}")
        }
    }

    /**
     * Executes [action] immediately on the main thread if the SDK is initialized,
     * or queues it safely to run once `onInitializationComplete()` resolves.
     */
    fun runWhenInitialized(context: Context, action: () -> Unit) {
        if (isSdkReady) {
            mainHandler.post { action() }
        } else {
            pendingInitActions.add(action)
            initialize(context, testMode)
        }
    }

    private fun drainPendingInitActions() {
        mainHandler.post {
            while (true) {
                val action = pendingInitActions.poll() ?: break
                try {
                    action.invoke()
                } catch (e: Exception) {
                    Log.w(TAG, "Error executing post-initialization action: ${e.message}")
                }
            }
        }
    }

    /**
     * Thread-safe Rewarded Video loader.
     * Guarantees `UnityAds.load()` is never called before `onInitializationComplete()`.
     */
    @Synchronized
    fun loadRewardedVideo(candidateIndex: Int = 0) {
        val networkManager = NetworkConnectivityManager.getInstanceOrNull()
        if (networkManager != null && !networkManager.isOnline.value) {
            Log.d(TAG, "UNITY_REWARDED_LOAD_ABORTED: Device is offline.")
            return
        }

        if (!isSdkReady) {
            Log.d(TAG, "UNITY_REWARDED_LOAD_DEFERRED: SDK not yet initialized. Queuing load after init.")
            pendingInitActions.add { loadRewardedVideo(0) }
            return
        }

        if (isRewardedLoaded.get()) {
            Log.d(TAG, "UNITY_REWARDED_ALREADY_LOADED: Placement $activeRewardedPlacement is ready.")
            return
        }

        if (candidateIndex == 0 && !isRewardedLoading.compareAndSet(false, true)) {
            Log.d(TAG, "UNITY_REWARDED_LOAD_IN_PROGRESS: Placement $activeRewardedPlacement is already loading.")
            return
        }

        val targetPlacement = REWARDED_CANDIDATES.getOrElse(candidateIndex) { PLACEMENT_REWARDED }
        activeRewardedPlacement = targetPlacement
        Log.d(TAG, "UNITY_REWARDED_REQUESTED: Loading placement '$targetPlacement' (testMode=$testMode)")

        try {
            val loadListener = object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isRewardedLoading.set(false)
                    isRewardedLoaded.set(true)
                    activeRewardedPlacement = placementId
                    rewardedRetryCount = 0
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }

                    Log.d(TAG, "UNITY_REWARDED_LOADED: Successfully loaded placement '$placementId'")

                    if (isRewardedShowPending) {
                        isRewardedShowPending = false
                        val activity = pendingShowActivityRef?.get()
                        val onAdClosed = pendingOnAdClosedCallback ?: {}
                        pendingShowActivityRef = null
                        pendingOnAdClosedCallback = null

                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            mainHandler.post {
                                presentLoadedRewardedAd(activity, onAdClosed)
                            }
                        }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    Log.w(
                        TAG,
                        "UNITY_REWARDED_LOAD_NOTICE: placement='$placementId', error=${error.name}, message='$message'"
                    )

                    // If placement ID was not found on dashboard, try the next candidate once
                    if (error == UnityAds.UnityAdsLoadError.INVALID_ARGUMENT &&
                        candidateIndex + 1 < REWARDED_CANDIDATES.size
                    ) {
                        loadRewardedVideo(candidateIndex + 1)
                        return
                    }

                    isRewardedLoading.set(false)
                    isRewardedLoaded.set(false)

                    // Notify user if they were actively waiting for the ad to open
                    if (isRewardedShowPending) {
                        isRewardedShowPending = false
                        val activity = pendingShowActivityRef?.get()
                        pendingShowActivityRef = null
                        pendingOnAdClosedCallback = null

                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            mainHandler.post {
                                Toast.makeText(
                                    activity,
                                    "Advertisement is currently unavailable. Please try again later.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    // Only retry transient network / NO_FILL errors, never INVALID_ARGUMENT or INTERNAL_ERROR
                    val isTransientError = error == UnityAds.UnityAdsLoadError.NO_FILL ||
                        error == UnityAds.UnityAdsLoadError.TIMEOUT
                    val isOnline = NetworkConnectivityManager.getInstanceOrNull()?.isOnline?.value ?: true
                    if (isOnline && isTransientError && rewardedRetryCount < 2) {
                        val delayMs = (INITIAL_RETRY_DELAY_MS * (1 shl rewardedRetryCount))
                            .coerceAtMost(MAX_RETRY_DELAY_MS)
                        rewardedRetryCount++
                        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable { loadRewardedVideo(0) }
                        rewardedRetryRunnable = runnable
                        mainHandler.postDelayed(runnable, delayMs)
                    }
                }
            }

            UnityAds.load(targetPlacement, loadListener)
        } catch (e: Exception) {
            isRewardedLoading.set(false)
            isRewardedLoaded.set(false)
            Log.w(TAG, "UNITY_REWARDED_LOAD_EXCEPTION: ${e.message}")
        }
    }

    /**
     * Shows the Rewarded Video ad when the user taps the Gift icon on the Home Screen.
     */
    fun showRewardedVideo(
        activity: Activity,
        onAdClosed: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        val networkManager = NetworkConnectivityManager.getInstance(activity)
        if (!networkManager.isOnline.value) {
            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Internet connection required to watch advertisements.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (isAdShowing.get()) {
            Log.d(TAG, "UNITY_REWARDED_SHOW_IGNORED: An advertisement is already on screen.")
            return
        }

        if (!isSdkReady) {
            Log.d(TAG, "UNITY_REWARDED_SHOW_QUEUED: SDK not initialized yet. Initializing and queuing presentation.")
            isRewardedShowPending = true
            pendingShowActivityRef = WeakReference(activity)
            pendingOnAdClosedCallback = onAdClosed

            mainHandler.post {
                Toast.makeText(activity, "Loading advertisement...", Toast.LENGTH_SHORT).show()
            }

            initialize(activity, testMode) {
                loadRewardedVideo(0)
            }
            return
        }

        if (isRewardedLoaded.get()) {
            presentLoadedRewardedAd(activity, onAdClosed)
            return
        }

        if (isRewardedLoading.get()) {
            Log.d(TAG, "UNITY_REWARDED_SHOW_QUEUED: Ad is currently loading; will auto-present on load complete.")
            isRewardedShowPending = true
            pendingShowActivityRef = WeakReference(activity)
            pendingOnAdClosedCallback = onAdClosed

            mainHandler.post {
                Toast.makeText(
                    activity,
                    "Loading advertisement, opening shortly...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        Log.d(TAG, "UNITY_REWARDED_SHOW_QUEUED: Starting new load request with auto-presentation enabled.")
        isRewardedShowPending = true
        pendingShowActivityRef = WeakReference(activity)
        pendingOnAdClosedCallback = onAdClosed

        mainHandler.post {
            Toast.makeText(activity, "Loading advertisement...", Toast.LENGTH_SHORT).show()
        }
        loadRewardedVideo(0)
    }

    private fun presentLoadedRewardedAd(
        activity: Activity,
        onAdClosed: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (!isAdShowing.compareAndSet(false, true)) {
            return
        }

        isRewardedLoaded.set(false)
        val targetPlacement = activeRewardedPlacement
        Log.d(TAG, "UNITY_REWARDED_DISPLAYED: Calling UnityAds.show for placement '$targetPlacement'")

        try {
            val showListener = object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "UNITY_REWARDED_START: Playback started for placement '$placementId'")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "UNITY_REWARDED_CLICK: User clicked ad for placement '$placementId'")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isAdShowing.set(false)
                    Log.d(
                        TAG,
                        "UNITY_REWARDED_COMPLETED: placement='$placementId', state=${state.name}"
                    )
                    loadRewardedVideo(0)
                    mainHandler.post { onAdClosed() }
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isAdShowing.set(false)
                    Log.w(
                        TAG,
                        "UNITY_REWARDED_SHOW_FAILURE: placement='$placementId', error=${error.name}, message='$message'"
                    )
                    mainHandler.post {
                        Toast.makeText(
                            activity,
                            "Advertisement unavailable right now.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    loadRewardedVideo(0)
                    mainHandler.post { onAdClosed() }
                }
            }

            UnityAds.show(
                activity,
                targetPlacement,
                UnityAdsShowOptions(),
                showListener
            )
        } catch (e: Exception) {
            isAdShowing.set(false)
            Log.w(TAG, "UNITY_REWARDED_SHOW_EXCEPTION: ${e.message}")
            loadRewardedVideo(0)
            mainHandler.post { onAdClosed() }
        }
    }

    /**
     * Thread-safe Interstitial loader.
     * Guarantees `UnityAds.load()` is never called before `onInitializationComplete()`.
     */
    @Synchronized
    fun loadInterstitial(candidateIndex: Int = 0) {
        val networkManager = NetworkConnectivityManager.getInstanceOrNull()
        if (networkManager != null && !networkManager.isOnline.value) {
            return
        }

        if (!isSdkReady) {
            pendingInitActions.add { loadInterstitial(0) }
            return
        }

        if (isInterstitialLoaded.get()) {
            return
        }

        if (candidateIndex == 0 && !isInterstitialLoading.compareAndSet(false, true)) {
            return
        }

        val targetPlacement = INTERSTITIAL_CANDIDATES.getOrElse(candidateIndex) { PLACEMENT_INTERSTITIAL }
        activeInterstitialPlacement = targetPlacement
        Log.d(TAG, "UNITY_INTERSTITIAL_REQUESTED: Loading placement '$targetPlacement' (testMode=$testMode)")

        try {
            val loadListener = object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isInterstitialLoading.set(false)
                    isInterstitialLoaded.set(true)
                    activeInterstitialPlacement = placementId
                    interstitialRetryCount = 0
                    interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    Log.d(TAG, "UNITY_INTERSTITIAL_LOADED: Successfully loaded placement '$placementId'")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    Log.w(
                        TAG,
                        "UNITY_INTERSTITIAL_LOAD_NOTICE: placement='$placementId', error=${error.name}, message='$message'"
                    )

                    if (error == UnityAds.UnityAdsLoadError.INVALID_ARGUMENT &&
                        candidateIndex + 1 < INTERSTITIAL_CANDIDATES.size
                    ) {
                        loadInterstitial(candidateIndex + 1)
                        return
                    }

                    isInterstitialLoading.set(false)
                    isInterstitialLoaded.set(false)

                    val isTransientError = error == UnityAds.UnityAdsLoadError.NO_FILL ||
                        error == UnityAds.UnityAdsLoadError.TIMEOUT
                    val isOnline = NetworkConnectivityManager.getInstanceOrNull()?.isOnline?.value ?: true
                    if (isOnline && isTransientError && interstitialRetryCount < 2) {
                        val delayMs = (INITIAL_RETRY_DELAY_MS * (1 shl interstitialRetryCount))
                            .coerceAtMost(MAX_RETRY_DELAY_MS)
                        interstitialRetryCount++
                        interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable { loadInterstitial(0) }
                        interstitialRetryRunnable = runnable
                        mainHandler.postDelayed(runnable, delayMs)
                    }
                }
            }

            UnityAds.load(targetPlacement, loadListener)
        } catch (e: Exception) {
            isInterstitialLoading.set(false)
            isInterstitialLoaded.set(false)
            Log.w(TAG, "UNITY_INTERSTITIAL_LOAD_EXCEPTION: ${e.message}")
        }
    }

    /**
     * Displays a loaded Interstitial ad if available.
     */
    fun showInterstitial(
        activity: Activity,
        onAdClosed: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdClosed()
            return
        }

        val isOnline = NetworkConnectivityManager.getInstance(activity).isOnline.value
        if (!isOnline || !isSdkReady || !isInterstitialLoaded.get()) {
            if (isOnline && isSdkReady) {
                loadInterstitial(0)
            }
            onAdClosed()
            return
        }

        if (!isAdShowing.compareAndSet(false, true)) {
            onAdClosed()
            return
        }

        isInterstitialLoaded.set(false)
        val targetPlacement = activeInterstitialPlacement
        Log.d(TAG, "UNITY_INTERSTITIAL_DISPLAYED: Showing placement '$targetPlacement'")

        try {
            val showListener = object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "UNITY_INTERSTITIAL_START: Playback started for '$placementId'")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "UNITY_INTERSTITIAL_CLICK: User clicked interstitial '$placementId'")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isAdShowing.set(false)
                    Log.d(TAG, "UNITY_INTERSTITIAL_COMPLETED: placement='$placementId', state=${state.name}")
                    loadInterstitial(0)
                    mainHandler.post { onAdClosed() }
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isAdShowing.set(false)
                    Log.w(
                        TAG,
                        "UNITY_INTERSTITIAL_SHOW_FAILURE: placement='$placementId', error=${error.name}, message='$message'"
                    )
                    loadInterstitial(0)
                    mainHandler.post { onAdClosed() }
                }
            }

            UnityAds.show(
                activity,
                targetPlacement,
                UnityAdsShowOptions(),
                showListener
            )
        } catch (e: Exception) {
            isAdShowing.set(false)
            Log.w(TAG, "UNITY_INTERSTITIAL_SHOW_EXCEPTION: ${e.message}")
            loadInterstitial(0)
            mainHandler.post { onAdClosed() }
        }
    }

    /**
     * Called when validated internet connectivity is restored.
     */
    fun onNetworkRestored(context: Context) {
        Log.d(TAG, "Network connectivity restored: checking Unity Ads readiness.")
        if (!isSdkReady) {
            initialize(context, testMode)
        } else {
            if (!isRewardedLoaded.get() && !isRewardedLoading.get()) {
                loadRewardedVideo(0)
            }
            if (!isInterstitialLoaded.get() && !isInterstitialLoading.get()) {
                loadInterstitial(0)
            }
        }
    }

    /**
     * Called when internet connectivity is lost. Cancels pending retry timers.
     */
    fun onNetworkLost() {
        Log.d(TAG, "Network connectivity lost: cancelling scheduled Unity Ads retry timers.")
        initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    internal fun getBannerCandidates(): List<String> = BANNER_CANDIDATES
}

/**
 * Lifecycle-safe Jetpack Compose Banner Ad View (`UnityBannerAdView`).
 *
 * - Waits until `UnityAdsManager.isSdkReadyState` confirms `onInitializationComplete()` has fired
 *   before calling `BannerView.load()`.
 * - Wraps the legacy Android `BannerView` inside an `AndroidView` interop layout factory with explicit
 *   `ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)`.
 * - Enforces a hardcoded `Modifier.height(50.dp)` to protect the Compose UI tree layout from breaking
 *   or throwing layout lifecycle exceptions.
 */
@Composable
fun UnityBannerAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val networkManager = remember(context) { NetworkConnectivityManager.getInstance(context) }
    val isOnline by networkManager.isOnline.collectAsState()
    val isSdkReady by UnityAdsManager.isSdkReadyState.collectAsState()

    val candidates = remember { UnityAdsManager.getBannerCandidates() }
    var candidateIndex by remember { mutableStateOf(0) }
    val bannerPlacementId = candidates.getOrElse(candidateIndex) { UnityAdsManager.PLACEMENT_BANNER }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        if (activity != null && isOnline && isSdkReady && UnityAds.isInitialized) {
            val bannerView = remember(activity, bannerPlacementId) {
                BannerView(
                    activity,
                    bannerPlacementId,
                    UnityBannerSize(320, 50)
                ).apply {
                    listener = object : BannerView.IListener {
                        override fun onBannerLoaded(bannerAdView: BannerView?) {
                            Log.d(
                                "UnityAdsManager",
                                "UNITY_BANNER_LOADED: Banner loaded for placement '${bannerAdView?.placementId ?: bannerPlacementId}'"
                            )
                        }

                        override fun onBannerShown(bannerAdView: BannerView?) {
                            Log.d(
                                "UnityAdsManager",
                                "UNITY_BANNER_SHOWN: Banner impression tracked for placement '${bannerAdView?.placementId ?: bannerPlacementId}'"
                            )
                        }

                        override fun onBannerClick(bannerAdView: BannerView?) {
                            Log.d(
                                "UnityAdsManager",
                                "UNITY_BANNER_CLICKED: Banner clicked for placement '${bannerAdView?.placementId ?: bannerPlacementId}'"
                            )
                        }

                        override fun onBannerFailedToLoad(
                            bannerAdView: BannerView?,
                            errorInfo: BannerErrorInfo?
                        ) {
                            Log.w(
                                "UnityAdsManager",
                                "UNITY_BANNER_LOAD_NOTICE: placement='${bannerAdView?.placementId ?: bannerPlacementId}', " +
                                    "errorCode=${errorInfo?.errorCode}, errorMessage='${errorInfo?.errorMessage}'"
                            )
                            val msg = errorInfo?.errorMessage.orEmpty()
                            if (msg.contains("could not be found", ignoreCase = true) &&
                                candidateIndex + 1 < candidates.size
                            ) {
                                candidateIndex += 1
                            }
                        }

                        override fun onBannerLeftApplication(bannerAdView: BannerView?) {
                            Log.d(
                                "UnityAdsManager",
                                "UNITY_BANNER_LEFT_APP: User left application from banner '${bannerAdView?.placementId ?: bannerPlacementId}'"
                            )
                        }
                    }
                }
            }

            DisposableEffect(bannerView) {
                Log.d(
                    "UnityAdsManager",
                    "UNITY_BANNER_REQUESTED: Calling BannerView.load() for placement '$bannerPlacementId' (testMode=${UnityAdsManager.testMode})"
                )
                bannerView.load()

                onDispose {
                    Log.d(
                        "UnityAdsManager",
                        "UNITY_BANNER_DESTROYED: Destroying BannerView for placement '$bannerPlacementId'"
                    )
                    try {
                        (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                        bannerView.destroy()
                    } catch (e: Exception) {
                        Log.w("UnityAdsManager", "Error destroying BannerView: ${e.message}")
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                        val bannerLayoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = Gravity.CENTER
                        }
                        addView(bannerView, bannerLayoutParams)
                    }
                },
                update = { container ->
                    if (bannerView.parent !== container) {
                        (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                        val bannerLayoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = Gravity.CENTER
                        }
                        container.addView(bannerView, bannerLayoutParams)
                    }
                }
            )
        }
    }
}

/**
 * Backward-compatible alias for [UnityBannerAdView].
 */
@Composable
fun UnityBannerAd(
    modifier: Modifier = Modifier
) {
    UnityBannerAdView(modifier = modifier)
}

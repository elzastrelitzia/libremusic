package app.pulse.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import android.util.Log
import app.pulse.android.ui.screens.searchRoute
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.work.Configuration
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.android.service.PlayerService
import app.pulse.android.service.ServiceNotifications
import app.pulse.android.service.downloadState
import app.pulse.android.ui.components.BottomSheetMenu
import app.pulse.android.ui.components.BottomSheetState
import app.pulse.android.ui.components.rememberBottomSheetState
import app.pulse.android.ui.components.themed.LinearProgressIndicator
import app.pulse.android.ui.components.themed.LocalDockHiddenCount
import app.pulse.android.ui.components.themed.LocalNavigationState
import app.pulse.android.ui.components.themed.UpdateDialog
import app.pulse.core.ui.utils.isLandscape

import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.home.HomeScreen
import app.pulse.android.ui.screens.player.Player
import app.pulse.android.ui.screens.player.Thumbnail
import app.pulse.android.ui.screens.playlistRoute
import app.pulse.android.ui.screens.searchResultRoute
import app.pulse.android.ui.screens.settingsRoute
import app.pulse.android.utils.DisposableListener
import app.pulse.android.utils.KeyedCrossfade
import app.pulse.android.utils.LocalMonetCompat
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.collectProvidedBitmapAsState
import app.pulse.android.utils.forcePlay
import app.pulse.android.utils.intent
import kotlinx.collections.immutable.persistentListOf
import app.pulse.android.utils.invokeOnReady
import app.pulse.android.utils.isInPip
import app.pulse.android.utils.maybeEnterPip
import app.pulse.android.utils.maybeExitPip
import app.pulse.android.utils.setDefaultPalette
import app.pulse.android.utils.shouldBePlaying
import app.pulse.android.utils.toast
import app.pulse.compose.persist.LocalPersistMap
import app.pulse.compose.persist.PersistMap
import app.pulse.compose.preferences.PreferencesHolder
import app.pulse.core.ui.ColorMode
import app.pulse.core.ui.ColorSource
import app.pulse.core.ui.Darkness
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.SystemBarAppearance
import app.pulse.core.ui.amoled
import app.pulse.core.ui.appearance
import app.pulse.core.ui.rippleConfiguration
import app.pulse.core.ui.shimmerTheme
import app.pulse.core.data.utils.activityIntentBundle
import app.pulse.core.ui.utils.isAtLeastAndroid12
import app.pulse.core.data.utils.songBundle
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.BrowseBody
import app.pulse.providers.innertube.requests.playlistPage
import app.pulse.providers.innertube.requests.song
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.bitmapFactoryExifOrientationStrategy
import coil3.decode.ExifOrientationStrategy
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MainActivity"
private val coroutineScope = CoroutineScope(Dispatchers.IO)

// Viewmodel in order to avoid recreating the entire Player state (WORKAROUND)
class MainViewModel : ViewModel() {
    var binder: PlayerService.Binder? by mutableStateOf(null)
    var isReady by mutableStateOf(false)

    suspend fun awaitBinder(): PlayerService.Binder =
        binder ?: snapshotFlow { binder }.filterNotNull().first()

}

class MainActivity : ComponentActivity(), MonetColorsChangedListener {
    private val vm: MainViewModel by viewModels()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) vm.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vm.binder = null
            // Try to rebind, otherwise fail
            unbindService(this)
            bindService(intent<PlayerService>(), this, BIND_AUTO_CREATE)
        }
    }

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    override fun onStart() {
        super.onStart()
        bindService(intent<PlayerService>(), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupSplashScreen()
        super.onCreate(savedInstanceState)

        initializeSystemUI()
        initializeThemeEngine()

        intent?.let { handleIntent(it) }
        addOnNewIntentListener(::handleIntent)
    }

    private fun setupSplashScreen() {
        installSplashScreen().setKeepOnScreenCondition { !vm.isReady }
    }

    private fun initializeSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent()
    }

    private fun initializeThemeEngine() {
        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()

        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(this, false)
        monet.updateMonetColors()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppWrapper(
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) = with(AppearancePreferences) {
        val sampleBitmap = vm.binder.collectProvidedBitmapAsState()

        val systemDark = isSystemInDarkTheme()
        val isDark = remember(colorMode, systemDark) {
            colorMode == ColorMode.Dark || (colorMode == ColorMode.System && systemDark)
        }

        LaunchedEffect(isDark, colorSource) {
            if (isDark && colorSource == ColorSource.Pink) {
                colorSource = ColorSource.MaterialYou
            }
        }

        LaunchedEffect(app.pulse.android.preferences.DataPreferences.versionCheckPeriod) {
            if (app.pulse.android.preferences.DataPreferences.versionCheckPeriod != app.pulse.android.preferences.DataPreferences.VersionCheckPeriod.Off) {
                kotlinx.coroutines.delay(10_000)
                app.pulse.android.service.VersionCheckWorker.executeOneTime(this@MainActivity.applicationContext)
            }
        }

        val appearance = appearance(
            source = colorSource,
            mode = colorMode,
            darkness = darkness,
            materialAccentColor = Color(monet.getAccentColor(this@MainActivity)),
            sampleBitmap = sampleBitmap
        )

        SystemBarAppearance(palette = appearance.colorPalette)

        BoxWithConstraints(
            modifier = Modifier.background(appearance.colorPalette.background0) then modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides vm.binder,
                LocalCredentialManager provides Dependencies.credentialManager,
                LocalIndication provides ripple(),
                LocalRippleConfiguration provides rippleConfiguration(appearance = appearance),
                LocalShimmerTheme provides shimmerTheme(),
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalPersistMap provides Dependencies.application.persistMap,
                LocalMonetCompat provides monet
            ) {

                content()
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    @OptIn(ExperimentalLayoutApi::class)
    fun setContent() = setContent {
        LaunchedEffect(Unit) {
            vm.isReady = true
        }

        val windowInsets = WindowInsets.systemBars

        AppWrapper(
            modifier = Modifier.padding(
                WindowInsets
                    .displayCutout
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
        ) {
            val density = LocalDensity.current
            val bottomDp = with(density) { windowInsets.getBottom(density).toDp() }

            val imeVisible = WindowInsets.isImeVisible
            val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val animatedBottomDp by animateDpAsState(
                targetValue = if (imeVisible) 0.dp else bottomDp,
                label = ""
            )

            val playerBottomSheetState = rememberBottomSheetState(
                key = vm.binder,
                dismissedBound = 0.dp,
                collapsedBound = 0.dp,
                expandedBound = maxHeight,
                initialAnchor = BottomSheetState.Anchor.Dismissed
            )

            val dockHiddenCount by LocalDockHiddenCount.current
            val playerAwareWindowInsets = remember(
                bottomDp,
                animatedBottomDp,
                playerBottomSheetState.value,
                imeVisible,
                imeBottomDp,
                AppearancePreferences.compactDock,
                dockHiddenCount
            ) {
                // A hidden dock draws nothing, so reserving room for it leaves every
                // screen that hides it with an empty band along the bottom edge.
                val dockClearance = when {
                    dockHiddenCount > 0 -> 0.dp
                    AppearancePreferences.compactDock -> Dimensions.items.collapsedPlayerHeight + 96.dp
                    else -> 160.dp
                }
                val baseBottom = animatedBottomDp + dockClearance
                val bottom =
                    if (imeVisible) imeBottomDp.coerceAtLeast(playerBottomSheetState.value)
                    else playerBottomSheetState.value.coerceAtLeast(baseBottom)

                windowInsets
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add(WindowInsets(bottom = bottom))
            }

            val pip = isInPip(
                onChange = {
                    if (!it || vm.binder?.player?.shouldBePlaying != true) return@isInPip
                    playerBottomSheetState.expandSoft()
                }
            )

            KeyedCrossfade(state = pip) { currentPip ->
                if (currentPip) Thumbnail(
                    isShowingLyrics = true,
                    onShowLyrics = { },
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = { },
                    onOpenDialog = { },
                    likedAt = null,
                    setLikedAt = { },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    shouldShowSynchronizedLyrics = true,
                    setShouldShowSynchronizedLyrics = { },
                    showLyricsControls = false
                ) else CompositionLocalProvider(
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val dockScrolled = app.pulse.android.ui.components.themed.LocalDockScrolled.current
                    val dockScrollConnection = app.pulse.android.ui.components.themed.rememberDockScrollConnection(dockScrolled)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(dockScrollConnection)
                    ) {
                        val isDownloading by downloadState.collectAsState()

                        HomeScreen()

                        AnimatedVisibility(
                            visible = isDownloading,
                            modifier = Modifier.padding(playerAwareWindowInsets.asPaddingValues())
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }

                        AnimatedVisibility(
                            visible = dockHiddenCount == 0,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            FloatingDock(
                                onPlayerClick = { playerBottomSheetState.expandSoft() },
                                onSearchClick = { coroutineScope.launch { searchRoute.ensureGlobal("") } }
                            )
                        }

                        CompositionLocalProvider(
                            LocalAppearance provides LocalAppearance.current.let {
                                if (it.colorPalette.isDark && AppearancePreferences.darkness == Darkness.AMOLED) {
                                    it.copy(colorPalette = it.colorPalette.amoled())
                                } else it
                            }
                        ) {
                            Player(
                                layoutState = playerBottomSheetState,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        BottomSheetMenu()
                    }
                }
            }

            val pendingVersion = DataPreferences.pendingUpdateVersion
            val pendingUrl = DataPreferences.pendingUpdateUrl
            if (pendingVersion.isNotEmpty() && pendingUrl.isNotEmpty()) {
                UpdateDialog(
                    downloadUrl = pendingUrl,
                    onDismiss = {
                        DataPreferences.pendingUpdateVersion = ""
                        DataPreferences.pendingUpdateUrl = ""
                    }
                )
            }

            vm.binder?.player.DisposableListener {
                object : Player.Listener {
                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        when {
                            mediaItem == null -> {
                                maybeExitPip()
                                // Do nothing, let it stay dismissed
                            }

                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem.mediaMetadata.extras?.songBundle?.isFromPersistentQueue != true -> {
                                if (AppearancePreferences.openPlayer) playerBottomSheetState.expandSoft()
                                else Unit
                            }

                            playerBottomSheetState.dismissed -> {
                                // Do nothing, mini player is always visible
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleIntent(intent: Intent) = lifecycleScope.launch(Dispatchers.IO) {
        val extras = intent.extras?.activityIntentBundle

        when (intent.action) {
            Intent.ACTION_SEARCH -> {
                val query = extras?.query ?: return@launch
                extras.query = null

                searchResultRoute.ensureGlobal(query)
            }

            Intent.ACTION_APPLICATION_PREFERENCES -> settingsRoute.ensureGlobal()

            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data
                    ?: runCatching { extras?.text?.toUri() }.getOrNull()
                    ?: return@launch

                intent.data = null
                extras?.text = null

                handleUrl(uri, vm.awaitBinder())
            }

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = when (extras?.mediaFocus) {
                    null, "vnd.android.cursor.item/*" -> extras?.query ?: extras?.text
                    MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> extras.genre
                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> extras.artist
                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> extras.album
                    "vnd.android.cursor.item/audio" -> listOfNotNull(
                        extras.album,
                        extras.artist,
                        extras.genre,
                        extras.title
                    ).joinToString(separator = " ")

                    @Suppress("deprecation")
                    MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> extras.playlist

                    else -> null
                }

                if (!query.isNullOrBlank()) vm.awaitBinder().playFromSearch(query)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monet.removeMonetColorsChangedListener(this)
        _monet = null

        removeOnNewIntentListener(::handleIntent)
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        if (!isInitialChange) recreate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (AppearancePreferences.autoPip && vm.binder?.player?.shouldBePlaying == true) maybeEnterPip()
    }
}

@Composable
private fun FloatingDock(
    onPlayerClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationState = LocalNavigationState.current
    val isLandscape = isLandscape
    val progress = app.pulse.android.ui.components.themed.rememberDockMorphProgress()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val scope = rememberCoroutineScope()

    app.pulse.android.ui.components.themed.MorphingDock(
        progress = progress,
        navigationState = navigationState.value,
        onPlayerClick = onPlayerClick,
        onSearchClick = onSearchClick,
        onHomeClick = {
            // Land on QuickPicks, then unwind every screen between here and home.
            UIStatePreferences.homeScreenTabIndex = 0
            scope.launch {
                // Every RouteHandler registers its back callback with
                // enabled = child != null, so no enabled callback means HomeScreen
                // is the only screen left. Dispatching past that would hand the
                // press to the system and quit the app, hence the guard.
                while (backDispatcher?.hasEnabledCallbacks() == true) {
                    backDispatcher?.onBackPressed()
                    // A back press suspends until the predictive back gesture
                    // finishes, so give it a frame to land on the parent handler.
                    withFrameNanos { }
                }
            }
        },
        isLandscape = isLandscape,
        modifier = modifier
    )
}

context(context: Context)
@Suppress("CyclomaticComplexMethod")
fun handleUrl(
    uri: Uri,
    binder: PlayerService.Binder?
) {
    val path = uri.pathSegments.firstOrNull()
    Log.d(TAG, "Opening url: $uri ($path)")

    coroutineScope.launch {
        when (path) {
            "search" -> uri.getQueryParameter("q")?.let { query ->
                searchResultRoute.ensureGlobal(query)
            }

            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                val browseId = "VL$playlistId"

                if (playlistId.startsWith("OLAK5uy_")) Innertube.playlistPage(
                    body = BrowseBody(browseId = browseId)
                )
                    ?.getOrNull()
                    ?.let { page ->
                        page.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId
                            ?.let { albumRoute.ensureGlobal(it) }
                    } ?: withContext(Dispatchers.Main) {
                    context.toast(context.getString(R.string.error_url, uri))
                }
                else playlistRoute.ensureGlobal(
                    p0 = browseId,
                    p1 = uri.getQueryParameter("params"),
                    p2 = null,
                    p3 = playlistId.startsWith("RDCLAK5uy_")
                )
            }

            "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                artistRoute.ensureGlobal(channelId)
            }

            else -> when {
                path == "watch" -> uri.getQueryParameter("v")
                uri.host == "youtu.be" -> path
                else -> {
                    withContext(Dispatchers.Main) {
                        context.toast(context.getString(R.string.error_url, uri))
                    }
                    null
                }
            }?.let { videoId ->
                Innertube.song(videoId)?.getOrNull()?.let { song ->
                    withContext(Dispatchers.Main) {
                        binder?.player?.forcePlay(song.asMediaItem)
                    }
                }
            }
        }
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No player insets provided") }
val LocalCredentialManager = staticCompositionLocalOf { Dependencies.credentialManager }

class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .let {
                    if (isAtLeastAndroid12) it.detectUnsafeIntentLaunch()
                    else it
                }
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
        Dependencies.init(this)

        MonetCompat.debugLog = BuildConfig.DEBUG
        super.onCreate()

        applicationScope.launch {
            Dependencies.warmUp()
            MonetCompat.enablePaletteCompat()
            with(ServiceNotifications) { createAll() }
        }

    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.1)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil"))
                .maxSizeBytes(DataPreferences.coilDiskCacheMaxSize.bytes)
                .build()
        }
        .bitmapFactoryExifOrientationStrategy(ExifOrientationStrategy.IGNORE)
        .let { if (BuildConfig.DEBUG) it.logger(DebugLogger()) else it }
        .build()

    val persistMap = PersistMap()

    override val workManagerConfiguration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    val py by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(application))
        Python.getInstance()
    }

    private val module by lazy { py.getModule("download") }

    val quickjsPath by lazy {
        File(application.applicationInfo.nativeLibraryDir, "libqjs.so")
            .also { if (!it.canExecute()) it.setExecutable(true) }
    }

    fun runDownload(id: String): String = module
        .callAttr("download", quickjsPath.absolutePath, id)
        .toString()

    fun warmUp() {
        runCatching {
            module.callAttr("warm_up")
            Log.d("MusicEngine", "Music Engine Warmed up successfully")
        }.onFailure {
            Log.e("MusicEngine", "Failed to warm up music engine", it)
        }
    }

    fun upgradeYoutubeDl(packageName: String = "yt-dlp"): Boolean {
        val success = runCatching { module.callAttr("upgrade", packageName) }
            .also { it.exceptionOrNull()?.printStackTrace() }
            .isSuccess
        if (!success) Log.e("Python", "Upgrading $packageName resulted in non-zero exit code!")
        return success
    }

    val credentialManager by lazy { CredentialManager.create(application) }

    internal fun init(application: MainApplication) {
        this.application = application
    }
}

open class GlobalPreferencesHolder : PreferencesHolder(Dependencies.application, "preferences")

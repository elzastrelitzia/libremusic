package app.pulse.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import app.pulse.desktop.ui.components.MiniPlayer
import app.pulse.desktop.ui.constants.sizes.Sizes
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.pulse.desktop.service.PlayerService
import app.pulse.desktop.ui.LayoutShell
import app.pulse.desktop.ui.View
import app.pulse.desktop.ui.components.QueuePanel
import app.pulse.desktop.ui.screens.player.PlayerScreen
import app.pulse.desktop.ui.utils.logSystemInfo
import app.pulse.desktop.ui.utils.startFileLogging
import app.pulse.providers.innertube.Innertube.DiscoverPage

/** detect UI scale, make effective resolution ~1920px wide when OS reports no scale (X11) */
private fun detectDpiScale(window: java.awt.Window): Float {
    // env var override
    System.getenv("PULSE_DPI_SCALE")?.toFloatOrNull()?.let { override ->
        return override.coerceIn(1f, 3f)
    }
    return try {
        val gc = window.graphicsConfiguration
        // OS scale already in Skiko density, X11 falls back to 1920px-equivalent
        if (gc.defaultTransform.scaleX.coerceAtLeast(gc.normalizingTransform.scaleX) > 1f) {
            1f
        } else {
            (gc.device.displayMode.width / 1920f).coerceIn(1f, 3f)
        }
    } catch (_: Exception) {
        1f
    }
}

fun main() {
    startFileLogging()
    logSystemInfo()

    val os = System.getProperty("os.name").lowercase()
    if (os.contains("nix") || os.contains("nux")) {
        System.setProperty("sun.awt.noerasebackground", "true")
        System.setProperty("skiko.renderApi", "OPENGL")
        System.setProperty("sun.java2d.opengl", "true")
    }

    application {
        val player = remember { PlayerService() }

        DisposableEffect(Unit) {
            onDispose { player.dispose() }
        }

        var homePage by remember { mutableStateOf<Result<DiscoverPage>?>(null) }
        var activeView by remember { mutableStateOf(View.Home) }
        var searchQuery by remember { mutableStateOf("") }
        var showPlayer by remember { mutableStateOf(false) }
        var showQueue by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                player.dispose()
                exitApplication()
            },
            title = "Pulse",
            state = WindowState(width = Sizes.windowDefaultW.dp, height = Sizes.windowDefaultH.dp)
        ) {
            // set background directly on AWT window to prevent white flash on loonix/wayland
            // i found this on internet but this is not working on hyprland
            // idk about others DE
            // lemme know pls if someone knows how to fix this
            window.background = java.awt.Color(10, 10, 10)
            window.minimumSize = java.awt.Dimension(Sizes.windowMinW, Sizes.windowMinH)

            // scale UI beyond OS scale, no-op when the OS already scales (Windogs/macOS)
            val dpiScale = remember(window.graphicsConfiguration) { detectDpiScale(window) }
            val defaultDensity = LocalDensity.current
            val scaledDensity = remember(defaultDensity, dpiScale) {
                object : Density {
                    override val density: Float get() = defaultDensity.density * dpiScale
                    override val fontScale: Float get() = defaultDensity.fontScale
                }
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Box(Modifier.fillMaxSize()) {
                LayoutShell(
                    activeView = activeView,
                    onNavigate = { activeView = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    homePage = homePage?.getOrNull(),
                    onPageLoaded = { homePage = it },
                    onPlaySong = { song -> player.play(song) },
                    player = player
                )

                // Player screen overlay (on top of layout shell)
                AnimatedVisibility(
                    visible = showPlayer,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1e1e1e)).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }) {
                        PlayerScreen(
                            player = player,
                            onBack = { showPlayer = false }
                        )

                        QueuePanel(
                            visible = showQueue,
                            player = player,
                            onClose = { showQueue = false }
                        )
                    }
                }

                // MiniPlayer always visible, even on top of PlayerScreen
                val playerState by player.state.collectAsState()
                if (playerState.currentSong != null) {
                    MiniPlayer(
                        player = player,
                        isPlayerOpen = showPlayer,
                        onClick = {},
                        onOpenPlayer = { showPlayer = true },
                        onClosePlayer = { showPlayer = false },
                        onToggleQueue = { showQueue = !showQueue },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = Sizes.miniPlayerEndPad.dp,
                                end = Sizes.miniPlayerEndPad.dp,
                                bottom = Sizes.miniPlayerBottomPad.dp
                            )
                    )
                }
            }
            } // end CompositionLocalProvider
        }
    }
}

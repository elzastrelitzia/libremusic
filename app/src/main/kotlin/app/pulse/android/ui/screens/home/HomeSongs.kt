package app.pulse.android.ui.screens.home

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.core.data.models.Song
import app.pulse.core.data.models.toEntity
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.android.preferences.OrderPreferences
import app.pulse.android.query
import app.pulse.android.service.isLocal
import app.pulse.android.transaction
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.NewMenu
import app.pulse.android.ui.components.NewMenuDivider
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.components.themed.ConfirmationDialog
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.CollapsingHeaderContentSpacer
import app.pulse.android.ui.components.themed.HeaderIconButton
import app.pulse.android.ui.components.themed.HeaderPillRow
import app.pulse.android.ui.components.themed.InHistoryMediaItemMenu
import app.pulse.android.ui.components.themed.TextField
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.modifiers.swipeToClose
import app.pulse.android.ui.screens.Route
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.center
import app.pulse.android.utils.color
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.formatted
import app.pulse.android.utils.playingSong
import app.pulse.android.utils.secondary
import app.pulse.android.utils.semiBold
import app.pulse.compose.persist.persistList
import app.pulse.core.data.enums.SongSortBy
import app.pulse.core.data.enums.SortOrder
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.onOverlay
import app.pulse.core.ui.overlay
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

private val Song.formattedTotalPlayTime @Composable get() = totalPlayTimeMs.milliseconds.formatted

@Composable
fun HomeSongs(
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    HomeSongs(
        onSearchClick = onSearchClick,
        songProvider = {
            Database.songs(songSortBy, songSortOrder)
                .map { songs -> songs.filter { it.totalPlayTimeMs > 0L } }
        },
        sortBy = songSortBy,
        setSortBy = { songSortBy = it },
        sortOrder = songSortOrder,
        setSortOrder = { songSortOrder = it },
        title = stringResource(R.string.songs),
        persistTag = "home/songs"
    )
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@OptIn(UnstableApi::class)
@Route
@Composable
fun HomeSongs(
    onSearchClick: () -> Unit,
    songProvider: () -> Flow<List<Song>>,
    sortBy: SongSortBy,
    setSortBy: (SongSortBy) -> Unit,
    sortOrder: SortOrder,
    setSortOrder: (SortOrder) -> Unit,
    title: String,
    persistTag: String
) {
    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current

    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    var items by persistList<Song>(persistTag)
    val filteredItems by remember {
        derivedStateOf {
            filter?.lowercase()?.ifBlank { null }?.let { f ->
                items.filter {
                    f in it.title.lowercase() || f in it.artistsText?.lowercase().orEmpty()
                }.sortedBy { it.title }
            } ?: items
        }
    }
    var hidingSong: String? by rememberSaveable { mutableStateOf(null) }

    LaunchedEffect(sortBy, sortOrder, songProvider) {
        songProvider().collect { items = it.toPersistentList() }
    }

    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    Box {
    CollapsingHeader(
        title = title,
        lazyListState = lazyListState,
        headerActions = {
            var searching by rememberSaveable { mutableStateOf(false) }
            var isMenuVisible by rememberSaveable { mutableStateOf(false) }

            AnimatedContent(
                targetState = searching,
                label = ""
            ) { state ->
                if (state) {
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    TextField(
                        value = filter.orEmpty(),
                        onValueChange = { filter = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (filter.isNullOrBlank()) filter = ""
                            focusManager.clearFocus()
                        }),
                        hintText = stringResource(R.string.filter_placeholder),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (!it.hasFocus) {
                                    keyboardController?.hide()
                                    if (filter?.isBlank() == true) {
                                        filter = null
                                        searching = false
                                    }
                                }
                            }
                    )
                } else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeaderPillRow {
                        HeaderIconButton(
                            onClick = { searching = true },
                            icon = R.drawable.search,
                            color = colorPalette.text
                        )

                        Box {
                            HeaderIconButton(
                                icon = R.drawable.hamburger,
                                onClick = { isMenuVisible = !isMenuVisible }
                            )

                            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                                NewMenu(
                                    visible = isMenuVisible,
                                    onDismiss = { isMenuVisible = false }
                                ) {
                                    NewMenuEntry(
                                        icon = R.drawable.trending,
                                        text = "Sort by play time",
                                        checked = sortBy == SongSortBy.PlayTime,
                                        onClick = {
                                            setSortBy(SongSortBy.PlayTime)
                                            isMenuVisible = false
                                        }
                                    )
                                    NewMenuEntry(
                                        icon = R.drawable.text,
                                        text = "Sort by title",
                                        checked = sortBy == SongSortBy.Title,
                                        onClick = {
                                            setSortBy(SongSortBy.Title)
                                            isMenuVisible = false
                                        }
                                    )
                                    NewMenuEntry(
                                        icon = R.drawable.time,
                                        text = "Sort by date added",
                                        checked = sortBy == SongSortBy.DateAdded,
                                        onClick = {
                                            setSortBy(SongSortBy.DateAdded)
                                            isMenuVisible = false
                                        }
                                    )

                                    NewMenuDivider()

                                    NewMenuEntry(
                                        icon = R.drawable.arrow_up,
                                        text = "Sort order",
                                        secondaryText = if (sortOrder == SortOrder.Ascending) "Ascending" else "Descending",
                                        onClick = {
                                            setSortOrder(!sortOrder)
                                            isMenuVisible = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (items.isNotEmpty()) BasicText(
                        text = pluralStringResource(
                            R.plurals.song_count_plural,
                            items.size,
                            items.size
                        ),
                        style = typography.xs.secondary.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(CollapsingHeaderContentSpacer))
            }

            items(
                items = filteredItems,
                key = { song -> song.id }
            ) { song ->
                if (hidingSong == song.id) HideSongDialog(
                    song = song,
                    onDismiss = { hidingSong = null },
                    onConfirm = {
                        hidingSong = null
                        menuState.hide()
                    }
                )

                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                keyboardController?.hide()
                                menuState.display {
                                    InHistoryMediaItemMenu(
                                        song = song,
                                        onDismiss = menuState::hide,
                                        onHideFromDatabase = { hidingSong = song.id }
                                    )
                                }
                            },
                            onClick = {
                                keyboardController?.hide()
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    items.map { it.asMediaItem },
                                    items.indexOf(song)
                                )
                            }
                        )
                        .animateItem()
                        .let {
                            if (AppearancePreferences.swipeToHideSong) it.swipeToClose(
                                key = filteredItems,
                                requireUnconsumed = true
                            ) { animationJob ->
                                if (AppearancePreferences.swipeToHideSongConfirm)
                                    hidingSong = song.id
                                else {
                                    if (!song.isLocal) binder?.cache?.removeResource(song.id)
                                    transaction { Database.delete(song.toEntity()) }
                                }
                                animationJob.join()
                            } else it
                        },
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    onThumbnailContent = if (sortBy == SongSortBy.PlayTime) {
                        {
                            BasicText(
                                text = song.formattedTotalPlayTime,
                                style = typography.xxs.semiBold.center.color(colorPalette.onOverlay),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, colorPalette.overlay)
                                        ),
                                        shape = thumbnailShape.copy(
                                            topStart = CornerSize(0.dp),
                                            topEnd = CornerSize(0.dp)
                                        )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                    } else null,
                    isPlaying = playing && currentMediaId == song.id
                )
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            icon = null
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun HideSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current

    ConfirmationDialog(
        text = stringResource(R.string.confirm_hide_song),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            query {
                runCatching {
                    if (!song.isLocal) binder?.cache?.removeResource(song.id)
                    Database.delete(song.toEntity())
                }
            }
        },
        modifier = modifier
    )
}

// Row content, for convenience, doesn't need modifier/receiver
@Suppress("UnusedReceiverParameter", "ModifierMissing")
@Composable
fun RowScope.HeaderSongSortBy(
    sortBy: SongSortBy,
    setSortBy: (SongSortBy) -> Unit,
    sortOrder: SortOrder,
    setSortOrder: (SortOrder) -> Unit
) {
    val (colorPalette) = LocalAppearance.current

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (sortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = ""
    )

    HeaderIconButton(
        icon = R.drawable.trending,
        enabled = sortBy == SongSortBy.PlayTime,
        onClick = { setSortBy(SongSortBy.PlayTime) }
    )

    HeaderIconButton(
        icon = R.drawable.text,
        enabled = sortBy == SongSortBy.Title,
        onClick = { setSortBy(SongSortBy.Title) }
    )

    HeaderIconButton(
        icon = R.drawable.time,
        enabled = sortBy == SongSortBy.DateAdded,
        onClick = { setSortBy(SongSortBy.DateAdded) }
    )

    HeaderIconButton(
        icon = R.drawable.arrow_up,
        color = colorPalette.text,
        onClick = { setSortOrder(!sortOrder) },
        modifier = Modifier.graphicsLayer { rotationZ = sortOrderIconRotation }
    )
}

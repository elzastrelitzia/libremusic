package app.pulse.android.ui.screens.builtinplaylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.core.data.models.Song
import app.pulse.core.data.models.toSong
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.Header
import app.pulse.android.ui.components.themed.InHistoryMediaItemMenu
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.components.themed.ValueSelectorDialog
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.screens.home.HeaderSongSortBy
import app.pulse.android.utils.PlaylistDownloadIcon
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.enqueue
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.playingSong
import app.pulse.compose.persist.persistList
import app.pulse.core.data.enums.BuiltInPlaylist
import app.pulse.core.data.enums.SongSortBy
import app.pulse.core.data.enums.SortOrder
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.enumSaver
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun BuiltInPlaylistSongs(
    builtInPlaylist: BuiltInPlaylist,
    modifier: Modifier = Modifier
) = with(DataPreferences) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var songs by persistList<Song>("${builtInPlaylist.name}/songs")

    var sortBy by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SongSortBy.DateAdded) }
    var sortOrder by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SortOrder.Descending) }

    LaunchedEffect(binder, sortBy, sortOrder) {
        when (builtInPlaylist) {
            BuiltInPlaylist.Favorites -> Database.favorites(
                sortBy = sortBy,
                sortOrder = sortOrder
            )

            BuiltInPlaylist.Offline ->
                Database
                    .songsWithContentLength(
                        sortBy = sortBy,
                        sortOrder = sortOrder
                    )
                    .map { songs ->
                        songs.filter { binder?.isCached(it) ?: false }.map { it.song.toSong() }
                    }

            BuiltInPlaylist.Top -> combine(
                flow = topListPeriodProperty.stateFlow,
                flow2 = topListLengthProperty.stateFlow
            ) { period, length -> period to length }.flatMapLatest { (period, length) ->
                if (period.duration == null) Database
                    .songsByPlayTimeDesc(limit = length)
                    .distinctUntilChanged()
                    .cancellable()
                else Database
                    .trending(
                        limit = length,
                        period = period.duration.inWholeMilliseconds
                    )
                    .distinctUntilChanged()
                    .cancellable()
            }

            BuiltInPlaylist.History -> Database.history()
        }.collect { list -> songs = list.toImmutableList() }
    }

    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    title = when (builtInPlaylist) {
                        BuiltInPlaylist.Favorites -> stringResource(R.string.favorites)
                        BuiltInPlaylist.Offline -> stringResource(R.string.offline)
                        BuiltInPlaylist.Top -> stringResource(
                            R.string.format_my_top_playlist,
                            topListLength
                        )

                        BuiltInPlaylist.History -> stringResource(R.string.history)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.enqueue),
                        enabled = songs.isNotEmpty(),
                        onClick = {
                            binder?.player?.enqueue(songs.map { it.asMediaItem })
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (builtInPlaylist != BuiltInPlaylist.Offline) PlaylistDownloadIcon(
                        songs = songs.map { it.asMediaItem }.toImmutableList()
                    )

                    if (builtInPlaylist.sortable) HeaderSongSortBy(
                        sortBy = sortBy,
                        setSortBy = { sortBy = it },
                        sortOrder = sortOrder,
                        setSortOrder = { sortOrder = it }
                    )

                    if (builtInPlaylist == BuiltInPlaylist.Top) {
                        var dialogShowing by rememberSaveable { mutableStateOf(false) }

                        SecondaryTextButton(
                            text = topListPeriod.displayName(),
                            onClick = { dialogShowing = true }
                        )

                        if (dialogShowing) ValueSelectorDialog(
                            onDismiss = { dialogShowing = false },
                            title = stringResource(
                                R.string.format_view_top_of_header,
                                topListLength
                            ),
                            selectedValue = topListPeriod,
                            values = DataPreferences.TopListPeriod.entries.toImmutableList(),
                            onValueSelect = { topListPeriod = it },
                            valueText = { it.displayName() }
                        )
                    }
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, song -> song }
            ) { index, song ->
                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    when (builtInPlaylist) {
                                        BuiltInPlaylist.Offline -> InHistoryMediaItemMenu(
                                            song = song,
                                            onDismiss = menuState::hide
                                        )

                                        BuiltInPlaylist.Favorites,
                                        BuiltInPlaylist.Top,
                                        BuiltInPlaylist.History -> NonQueuedMediaItemMenu(
                                            mediaItem = song.asMediaItem,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                }
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    items = songs.map { it.asMediaItem },
                                    index = index
                                )
                            }
                        )
                        .animateItem(),
                    song = song,
                    index = if (builtInPlaylist == BuiltInPlaylist.Top) index else null,
                    thumbnailSize = Dimensions.thumbnails.song,
                    isPlaying = playing && currentMediaId == song.id
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState
        )
    }
}


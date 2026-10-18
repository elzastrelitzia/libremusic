package app.pulse.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.R
import app.pulse.android.models.Album
import app.pulse.android.preferences.OrderPreferences
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.CollapsingHeaderContentSpacer
import app.pulse.android.ui.components.themed.HeaderIconButton
import app.pulse.android.ui.components.themed.HeaderPillRow
import app.pulse.android.ui.components.NewMenu
import app.pulse.android.ui.components.NewMenuDivider
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.items.AlbumItem
import app.pulse.android.ui.screens.Route
import app.pulse.compose.persist.persist
import app.pulse.core.data.enums.AlbumSortBy
import app.pulse.core.data.enums.SortOrder
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance

@Route
@Composable
fun HomeAlbums(
    onAlbumClick: (Album) -> Unit,
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    val (colorPalette) = LocalAppearance.current

    var items by persist<List<Album>>(tag = "home/albums", emptyList())

    LaunchedEffect(albumSortBy, albumSortOrder) {
        Database.albums(albumSortBy, albumSortOrder).collect { items = it }
    }

    var isMenuVisible by rememberSaveable { mutableStateOf(false) }

    val lazyGridState = rememberLazyGridState()

    Box {
    CollapsingHeader(
        title = stringResource(R.string.albums),
        lazyGridState = lazyGridState,
        expandedFontSize = 36.sp,
        collapsedFontSize = 26.sp,
        headerActions = {
            HeaderPillRow {
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
                            icon = R.drawable.calendar,
                            text = "Sort by year",
                            checked = albumSortBy == AlbumSortBy.Year,
                            onClick = {
                                albumSortBy = AlbumSortBy.Year
                                isMenuVisible = false
                            }
                        )
                        NewMenuEntry(
                            icon = R.drawable.text,
                            text = "Sort by title",
                            checked = albumSortBy == AlbumSortBy.Title,
                            onClick = {
                                albumSortBy = AlbumSortBy.Title
                                isMenuVisible = false
                            }
                        )
                        NewMenuEntry(
                            icon = R.drawable.time,
                            text = "Sort by date added",
                            checked = albumSortBy == AlbumSortBy.DateAdded,
                            onClick = {
                                albumSortBy = AlbumSortBy.DateAdded
                                isMenuVisible = false
                            }
                        )

                        NewMenuDivider()

                        NewMenuEntry(
                            icon = R.drawable.arrow_up,
                            text = "Sort order",
                            secondaryText = if (albumSortOrder == SortOrder.Ascending) "Ascending" else "Descending",
                            onClick = {
                                albumSortOrder = !albumSortOrder
                                isMenuVisible = false
                            }
                        )
                        NewMenuEntry(
                            icon = if (UIStatePreferences.albumsAsGrid) R.drawable.grid else R.drawable.list,
                            text = "Layout",
                            secondaryText = if (UIStatePreferences.albumsAsGrid) "Grid" else "List",
                            onClick = {
                                UIStatePreferences.albumsAsGrid = !UIStatePreferences.albumsAsGrid
                                isMenuVisible = false
                            }
                        )
                    }
                }
            }
            }
        }
    ) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = if (UIStatePreferences.albumsAsGrid)
                GridCells.Adaptive(Dimensions.thumbnails.album + Dimensions.items.alternativePadding * 2)
            else GridCells.Fixed(1),
            contentPadding = PaddingValues(
                top = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
                    .calculateTopPadding() + 0.dp,
                bottom = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
                    .calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.items.alternativePadding),
            verticalArrangement = if (UIStatePreferences.albumsAsGrid)
                Arrangement.spacedBy(Dimensions.items.alternativePadding)
            else Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette.background0)
        ) {
            item(key = "spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(CollapsingHeaderContentSpacer))
            }

            items(
                items = items,
                key = Album::id
            ) { album ->
                AlbumItem(
                    album = album,
                    thumbnailSize = if (UIStatePreferences.albumsAsGrid)
                        Dimensions.thumbnails.album
                    else Dimensions.thumbnails.album - 24.dp,
                    alternative = UIStatePreferences.albumsAsGrid,
                    showChevron = true,
                    modifier = Modifier
                        .clickable(onClick = { onAlbumClick(album) })
                        .animateItem()
                )
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(
            lazyGridState = lazyGridState,
            icon = null
        )
    }
}

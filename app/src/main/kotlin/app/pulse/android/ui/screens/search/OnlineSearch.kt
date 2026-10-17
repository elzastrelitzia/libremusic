package app.pulse.android.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.models.SearchQuery
import app.pulse.android.models.SearchQueryArtwork
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.query
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.Header
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.items.SongItem
import app.pulse.android.utils.align
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.center
import app.pulse.android.utils.disabled
import app.pulse.android.utils.forcePlay
import app.pulse.android.utils.medium
import app.pulse.android.utils.secondary
import app.pulse.android.utils.thumbnail
import app.pulse.compose.persist.persist
import app.pulse.compose.persist.persistList
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.px
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.SearchBody
import app.pulse.providers.innertube.models.bodies.SearchSuggestionsBody
import app.pulse.providers.innertube.requests.searchPage
import app.pulse.providers.innertube.requests.searchSuggestions
import app.pulse.providers.innertube.utils.from
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OnlineSearch(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) = Box(modifier = modifier) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    var history by persistList<SearchQueryArtwork>("search/online/history")
    var suggestionsResult by persist<Result<List<String>?>?>("search/online/suggestionsResult")
    var previewResult by persist<Result<List<Innertube.SongItem>?>?>("search/online/previewResult")

    LaunchedEffect(textFieldValue.text) {
        if (DataPreferences.pauseSearchHistory) return@LaunchedEffect

        Database.queriesWithArtwork("%${textFieldValue.text}%")
            .distinctUntilChanged { old, new -> old.size == new.size }
            .collect { history = it.toImmutableList() }
    }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.isEmpty()) return@LaunchedEffect

        delay(500)
        suggestionsResult = Innertube.searchSuggestions(
            body = SearchSuggestionsBody(input = textFieldValue.text)
        )
    }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.length < 2) {
            previewResult = null
            return@LaunchedEffect
        }

        delay(600)
        previewResult = Innertube.searchPage(
            body = SearchBody(
                query = textFieldValue.text,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )?.map { page -> page?.items?.take(5) }
    }

    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(
            key = "header",
            contentType = 0
        ) {
            val container = LocalPinnableContainer.current

            DisposableEffect(Unit) {
                val handle = container?.pin()
                onDispose {
                    handle?.release()
                }
            }
        }

        previewResult?.getOrNull()?.let { previews ->
            items(
                items = previews,
                key = { it.key }
            ) { song ->
                SongItem(
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    modifier = Modifier.clickable {
                        binder?.stopRadio()
                        binder?.player?.forcePlay(song.asMediaItem)
                        binder?.setupRadio(song.info?.endpoint)
                    }
                )
            }
        }

        items(
            items = history,
            key = SearchQueryArtwork::id
        ) { searchQuery ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onSearch(searchQuery.query) }
                    .fillMaxWidth()
                    .padding(all = 16.dp)
                    .animateItem()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(64.dp)
                ) {
                    val thumbnailUrl = searchQuery.thumbnailUrl.thumbnail(40.dp.px)

                    if (thumbnailUrl != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(thumbnailUrl) {
                                ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.time),
                            contentDescription = null,
                            colorFilter = ColorFilter.disabled,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                BasicText(
                    text = searchQuery.query,
                    style = typography.m.medium,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .weight(1f)
                )

                Image(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    colorFilter = ColorFilter.disabled,
                    modifier = Modifier
                        .clickable(
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                query {
                                    Database.delete(
                                        SearchQuery(
                                            id = searchQuery.id,
                                            query = searchQuery.query
                                        )
                                    )
                                }
                            }
                        )
                        .padding(horizontal = 8.dp)
                        .size(20.dp)
                )

                Image(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    colorFilter = ColorFilter.disabled,
                    modifier = Modifier
                        .clickable(
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                onTextFieldValueChange(
                                    TextFieldValue(
                                        text = searchQuery.query,
                                        selection = TextRange(searchQuery.query.length)
                                    )
                                )
                            }
                        )
                        .rotate(225f)
                        .padding(horizontal = 8.dp)
                        .size(22.dp)
                )
            }
        }

        suggestionsResult?.getOrNull()?.let { suggestions ->
            items(items = suggestions) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSearch(suggestion) }
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                            .paint(
                                painter = painterResource(R.drawable.search),
                                colorFilter = ColorFilter.disabled
                            )
                    )

                    BasicText(
                        text = suggestion,
                        style = typography.s.secondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                    )

                    Image(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.disabled,
                        modifier = Modifier
                            .clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    onTextFieldValueChange(
                                        TextFieldValue(
                                            text = suggestion,
                                            selection = TextRange(suggestion.length)
                                        )
                                    )
                                }
                            )
                            .rotate(225f)
                            .padding(horizontal = 8.dp)
                            .size(22.dp)
                    )
                }
            }
        } ?: suggestionsResult?.exceptionOrNull()?.let {
            item {
                Box(modifier = Modifier.fillMaxSize()) {
                    BasicText(
                        text = stringResource(R.string.error_message),
                        style = typography.s.secondary.center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
}

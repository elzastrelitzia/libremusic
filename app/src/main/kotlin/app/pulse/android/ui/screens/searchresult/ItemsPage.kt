package app.pulse.android.ui.screens.searchresult

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.R
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.utils.center
import app.pulse.android.utils.secondary
import app.pulse.compose.persist.persist
import app.pulse.core.ui.LocalAppearance
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.utils.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    stickyHeader: Boolean = false,
    noinline backdrop: (@Composable () -> Unit)? = null,
    noinline provider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null
) = ItemsPage(
    tag = tag,
    header = { before, _ -> header(before) },
    itemContent = itemContent,
    itemPlaceholderContent = itemPlaceholderContent,
    modifier = modifier,
    initialPlaceholderCount = initialPlaceholderCount,
    continuationPlaceholderCount = continuationPlaceholderCount,
    emptyItemsText = emptyItemsText,
    stickyHeader = stickyHeader,
    backdrop = backdrop,
    provider = provider
)

@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    stickyHeader: Boolean = false,
    noinline backdrop: (@Composable () -> Unit)? = null,
    noinline provider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null
) {
    val (_, typography) = LocalAppearance.current
    val updatedProvider by rememberUpdatedState(provider)
    // Keyed by tag, plain remember (not saveable): Route equality ignores args, so a new
    // query reuses this composition and stale remembers. Keying by tag forces fresh state
    // (scroll + items) per query. Same tag re-entry keeps state.
    val lazyListState = remember(tag) { LazyListState() }
    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)

    val shouldLoad by remember(tag) {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" || it.key == "loading-first" }
        }
    }

    LaunchedEffect(shouldLoad, updatedProvider) {
        if (!shouldLoad) return@LaunchedEffect
        val provideItems = updatedProvider ?: return@LaunchedEffect

        withContext(Dispatchers.IO) {
            provideItems(itemsPage?.continuation)
        }?.onSuccess {
            if (it == null) {
                if (itemsPage == null) itemsPage = Innertube.ItemsPage(null, null)
            } else itemsPage += it
        }?.onFailure {
            itemsPage = itemsPage?.copy(continuation = null)
        }?.exceptionOrNull()?.printStackTrace()
    }

    val topInset = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val headerHeight = topInset + 64.dp

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = if (stickyHeader) LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
                .add(WindowInsets(top = headerHeight))
                .asPaddingValues()
            else LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!stickyHeader) item(
                key = "header",
                contentType = "header"
            ) {
                header(null, null)
            }

            items(
                items = itemsPage?.items ?: emptyList(),
                key = Innertube.Item::key,
                itemContent = itemContent
            )

            if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) item(key = "empty") {
                BasicText(
                    text = emptyItemsText,
                    style = typography.xs.secondary.center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .fillMaxWidth()
                )
            }

            if (!(itemsPage != null && itemsPage?.continuation == null)) item(
                // ponytail: first-load placeholder gets its own key. With the shared "loading"
                // key, Compose anchors scroll to it and follows it when page 1 moves it to the
                // end of the list, auto-scrolling results to item ~14 on load
                key = if (itemsPage?.items.isNullOrEmpty()) "loading-first" else "loading"
            ) {
                val isFirstLoad = itemsPage?.items.isNullOrEmpty()

                ShimmerHost(
                    modifier = if (isFirstLoad) Modifier.fillParentMaxSize() else Modifier
                ) {
                    repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                        itemPlaceholderContent()
                    }
                }
            }
        }

        // backdrop overlays the list: fade must draw over content to be visible
        backdrop?.invoke()

        // sticky header as fixed overlay above backdrop, always pinned so the fade can't hide it
        if (stickyHeader) Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset)
        ) {
            header(null, null)
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

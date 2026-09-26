package app.pulse.providers.innertube.requests

import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.BrowseResponse
import app.pulse.providers.innertube.models.Context
import app.pulse.providers.innertube.models.MusicCarouselShelfRenderer
import app.pulse.providers.innertube.models.NextResponse
import app.pulse.providers.innertube.models.bodies.BrowseBody
import app.pulse.providers.innertube.models.bodies.NextBody
import app.pulse.providers.innertube.utils.findSectionByStrapline
import app.pulse.providers.innertube.utils.findSectionByTitle
import app.pulse.providers.innertube.utils.from
import app.pulse.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
suspend fun Innertube.relatedPage(body: NextBody) = runCatchingCancellable {
    val nextResponse = client.post(NEXT) {
        setBody(body.copy(context = Context.DefaultWebNoLang))
        @Suppress("all")
        mask(
            "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(content.musicQueueRenderer.content.playlistPanelRenderer(continuations,contents(automixPreviewVideoRenderer,$PLAYLIST_PANEL_VIDEO_RENDERER_MASK)),endpoint,title)"
        )
    }.body<NextResponse>()

    val tabs = nextResponse
        .contents?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer?.watchNextTabbedResultsRenderer
        ?.tabs

    // Songs come from the autoplay panel, the same list the radio queue reads.
    // The "you might also like" tab is missing for plenty of tracks, so it cannot
    // be the source of the recommendations.
    val radioSongs = tabs
        ?.getOrNull(0)
        ?.tabRenderer
        ?.content
        ?.musicQueueRenderer
        ?.content
        ?.playlistPanelRenderer
        ?.toSongsPage()
        ?.items
        ?.distinctBy(Innertube.SongItem::key)

    val browseId = tabs
        ?.firstOrNull { tab ->
            tab.tabRenderer?.endpoint?.browseEndpoint?.browseId?.startsWith("MPTR") == true
        }
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId

    // Plenty of tracks have no "you might also like" tab. The autoplay panel is
    // the only source then, so the browse is optional rather than a dead end.
    val sectionListRenderer = browseId?.let { id ->
        client.post(BROWSE) {
            setBody(
                BrowseBody(
                    browseId = id,
                    context = Context.DefaultWebNoLang
                )
            )
            @Suppress("all")
            mask(
                "contents.sectionListRenderer.contents.musicCarouselShelfRenderer(header.musicCarouselShelfBasicHeaderRenderer(title,strapline),contents($MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK,$MUSIC_TWO_ROW_ITEM_RENDERER_MASK))"
            )
        }.body<BrowseResponse>()
            .contents
            ?.sectionListRenderer
    }

    val shelfSongs = sectionListRenderer
        ?.findSectionByTitle("You might also like")
        ?.musicCarouselShelfRenderer
        ?.contents
        ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from)

    // shelf wins when it has anything, it is the curated pick. otherwise the
    // autoplay panel, which always has something.
    // the seed track sits at index 0 of the panel and HomeQuickPicks already
    // renders it as its own item, so it would show up twice.
    val songs = (shelfSongs?.takeIf { it.isNotEmpty() } ?: radioSongs)
        ?.filterNot { it.key == body.videoId }

    val playlists = sectionListRenderer
        ?.findSectionByTitle("Recommended playlists")
        ?.musicCarouselShelfRenderer
        ?.contents
        ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
        ?.mapNotNull(Innertube.PlaylistItem::from)
        ?.sortedByDescending { it.channel?.name == "YouTube Music" }

    val albums = sectionListRenderer
        ?.findSectionByStrapline("MORE FROM")
        ?.musicCarouselShelfRenderer
        ?.contents
        ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
        ?.mapNotNull(Innertube.AlbumItem::from)

    val artists = sectionListRenderer
        ?.findSectionByTitle("Similar artists")
        ?.musicCarouselShelfRenderer
        ?.contents
        ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
        ?.mapNotNull(Innertube.ArtistItem::from)

    Innertube.RelatedPage(
        songs = songs,
        playlists = playlists,
        albums = albums,
        artists = artists
    )
}

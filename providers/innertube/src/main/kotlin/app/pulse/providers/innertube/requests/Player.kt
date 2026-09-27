package app.pulse.providers.innertube.requests

import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.Context
import app.pulse.providers.innertube.models.PlayerResponse
import app.pulse.providers.innertube.models.bodies.PlayerBody
import app.pulse.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.generateNonce
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    checkIsValid: Boolean,
    vararg contexts: Context
): PlayerResponse? {
    contexts.forEach { rawContext ->
        if (!currentCoroutineContext().isActive) return null
        val context = sessionVisitorData?.let { visitor ->
            rawContext.copy(client = rawContext.client.copy(defaultVisitorData = visitor))
        } ?: rawContext
        if (isStreamClientBlocked(body.videoId, context.client.clientName)) {
            logger.info("Skipping blocked stream client ${context.client.clientName} for ${body.videoId}")
            return@forEach
        }

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")
        val cpn = generateNonce(16).decodeToString()
        runCatchingCancellable {
            client.post(if (context.client.music) PLAYER_MUSIC else PLAYER) {
                setBody(
                    body.copy(
                        context = context,
                        cpn = cpn
                    )
                )

                context.apply()

                parameter("t", generateNonce(12))
                header("X-Goog-Api-Format-Version", "2")
                parameter("id", body.videoId)
            }.body<PlayerResponse>().also {
                // Not "Got $it": toString() on PlayerResponse dumps every signed
                // googlevideo URL, which is ~15 KB per request and no use to read.
                val formats = it.streamingData?.adaptiveFormats.orEmpty()
                logger.info(
                    "Got status=${it.playabilityStatus?.status} " +
                        "reason=${it.playabilityStatus?.reason} " +
                        "formats=${formats.size} " +
                        "audio=${formats.count { f -> f.mimeType?.startsWith("audio") == true }} " +
                        "loudnessDb=${it.playerConfig?.audioConfig?.loudnessDb}"
                )
            }
        }
            ?.getOrNull()
            ?.takeIf { checkIsValid && it.isValid }
            ?.let {
                markStreamClientSuccessful(body.videoId, context.client.clientName)
                return it.copy(
                    cpn = cpn,
                    context = context
                )
            }
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

private val Innertube.playbackContexts
    get() = listOf(
        Context.DefaultAndroidVr,
        Context.DefaultIOS,
        Context.DefaultWeb,
        Context.DefaultAndroidMusic,
        Context.DefaultTV
    )

suspend fun Innertube.player(
    body: PlayerBody,
    checkIsValid: Boolean = true
): Result<PlayerResponse?>? = runCatchingCancellable {
    ensureVisitorData()
    tryContexts(
        body = body,
        checkIsValid = checkIsValid,
        *playbackContexts.toTypedArray()
    )
}

package app.pulse.android.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.R
import app.pulse.android.ui.components.themed.Header
import app.pulse.android.ui.components.themed.HeaderIconButton
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.components.themed.SegmentedControl
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.Route
import app.pulse.android.utils.align
import app.pulse.android.utils.medium
import app.pulse.android.utils.secondary
import app.pulse.compose.persist.PersistMapCleanup
import app.pulse.compose.routing.RouteHandler
import app.pulse.core.ui.LocalAppearance
import io.ktor.http.Url
import kotlinx.coroutines.delay

@Route
@Composable
fun SearchScreen(
    initialTextInput: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabChanged) = rememberSaveable { mutableIntStateOf(0) }

    val (textFieldValue, onTextFieldValueChanged) = rememberSaveable(
        initialTextInput,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    PersistMapCleanup(prefix = "search/")

    RouteHandler {
        GlobalRoutes()

        Content {
            val (colorPalette, typography) = LocalAppearance.current
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                delay(300)
                focusRequester.requestFocus()
            }

            val decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
                Box {
                    AnimatedVisibility(
                        visible = textFieldValue.text.isEmpty(),
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        BasicText(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                            style = typography.l.secondary.copy(color = colorPalette.textSecondary.copy(alpha = 0.6f))
                        )
                    }
                    innerTextField()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorPalette.background0)
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Top + WindowInsetsSides.End)
                            .asPaddingValues()
                    )
            ) {
                SegmentedControl(
                    segments = listOf(stringResource(R.string.online), stringResource(R.string.library)),
                    selectedSegment = tabIndex,
                    onSegmentSelected = onTabChanged,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                )

                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onTextFieldValueChanged,
                            textStyle = typography.l.medium.align(TextAlign.Start),
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (textFieldValue.text.isNotEmpty()) onSearch(textFieldValue.text)
                                }
                            ),
                            cursorBrush = SolidColor(colorPalette.text.copy(alpha = 0.4f)),
                            decorationBox = decorationBox,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )

                        if (textFieldValue.text.isNotEmpty()) {
                            HeaderIconButton(
                                icon = R.drawable.close,
                                onClick = { onTextFieldValueChanged(TextFieldValue()) },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colorPalette.textSecondary.copy(alpha = 0.2f))
                    )

                    val playlistId = remember(textFieldValue.text) {
                        runCatching {
                            Url(textFieldValue.text).takeIf {
                                it.host.endsWith("youtube.com", ignoreCase = true) &&
                                    it.segments.lastOrNull()?.equals("playlist", ignoreCase = true) == true
                            }?.parameters?.get("list")
                        }.getOrNull()
                    }

                    if (tabIndex == 0 && playlistId != null) {
                        val isAlbum = playlistId.startsWith("OLAK5uy_")
                        SecondaryTextButton(
                            text = if (isAlbum) stringResource(R.string.view_album) else stringResource(R.string.view_playlist),
                            onClick = { onViewPlaylist(textFieldValue.text) },
                            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    saveableStateHolder.SaveableStateProvider(tabIndex) {
                        when (tabIndex) {
                            0 -> OnlineSearch(
                                textFieldValue = textFieldValue,
                                onTextFieldValueChange = onTextFieldValueChanged,
                                onSearch = onSearch
                            )
                            1 -> LocalSongSearch(
                                textFieldValue = textFieldValue,
                                onTextFieldValueChange = onTextFieldValueChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

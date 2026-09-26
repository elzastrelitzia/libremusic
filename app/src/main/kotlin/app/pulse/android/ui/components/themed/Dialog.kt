package app.pulse.android.ui.components.themed

import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pulse.android.R
import app.pulse.android.utils.center
import app.pulse.android.utils.drawCircle
import app.pulse.android.utils.medium
import app.pulse.android.utils.semiBold
import app.pulse.core.ui.LocalAppearance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/**
 * The card every dialog in the app sits in: a fixed 320dp wide panel with a 40dp
 * radius. Callers keep their own `Dialog` wrapper so they can decide what an
 * outside tap does, which is the only thing that differs between them.
 */
@Composable
internal fun AppleDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Column(
    modifier = modifier
        .width(320.dp)
        .clip(RoundedCornerShape(40.dp))
        .background(LocalAppearance.current.colorPalette.background1)
        .padding(14.dp),
    content = content
)

@Composable
fun TextFieldDialog(
    hintText: String,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    initialTextInput: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onCancel: () -> Unit = onDismiss,
    isTextInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardOptions: KeyboardOptions = KeyboardOptions()
) {
    val (palette, typography) = LocalAppearance.current
    val focusRequester = remember { FocusRequester() }

    var value by rememberSaveable(initialTextInput) { mutableStateOf(initialTextInput) }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    fun accept() {
        if (!isTextInputValid(value)) return
        onDismiss()
        onAccept(value)
    }

    Dialog(onDismissRequest = onDismiss) {
        AppleDialogSurface(modifier = modifier) {
            if (title.isNotEmpty()) {
                BasicText(
                    text = title,
                    style = typography.s.copy(
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            // A pill for a single line, a rounded rectangle for a block: a 50dp
            // radius stretched over ten lines of lyrics reads as a mistake. The
            // height cap keeps a multiline field from pushing the buttons off
            // a short screen.
            val fieldShape = RoundedCornerShape(if (singleLine) 50.dp else 14.dp)

            TextField(
                value = value,
                onValueChange = { value = it },
                textStyle = typography.xs.semiBold.center,
                singleLine = singleLine,
                maxLines = maxLines,
                hintText = hintText,
                keyboardActions = KeyboardActions(onDone = { accept() }),
                keyboardOptions = keyboardOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(palette.background2, fieldShape)
                    .border(1.dp, palette.text.copy(alpha = 0.12f), fieldShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppleDialogButton(
                    text = cancelText,
                    containerColor = palette.background2,
                    contentColor = palette.text,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCancel()
                        onDismiss()
                    }
                )
                AppleDialogButton(
                    text = doneText,
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    enabled = isTextInputValid(value),
                    modifier = Modifier.weight(1f),
                    onClick = { accept() }
                )
            }
        }
    }
}

@Composable
fun <T> NumberFieldDialog(
    onDismiss: () -> Unit,
    onAccept: (T) -> Unit,
    initialValue: T,
    defaultValue: T,
    convert: (String) -> T?,
    range: ClosedRange<T>,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    onCancel: () -> Unit = onDismiss
) where T : Number, T : Comparable<T> = TextFieldDialog(
    hintText = "",
    onDismiss = onDismiss,
    onAccept = { onAccept((convert(it) ?: defaultValue).coerceIn(range)) },
    modifier = modifier,
    cancelText = cancelText,
    doneText = doneText,
    initialTextInput = initialValue.toString(),
    onCancel = onCancel,
    isTextInputValid = { true },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
)

@Composable
fun ConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss
) = DefaultDialog(
    onDismiss = onDismiss,
    modifier = modifier
) {
    ConfirmationDialogBody(
        text = text,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        cancelText = cancelText,
        confirmText = confirmText,
        onCancel = onCancel
    )
}

@Suppress("ModifierMissing", "UnusedReceiverParameter")
@Composable
fun ColumnScope.ConfirmationDialogBody(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss
) {
    val (palette, typography) = LocalAppearance.current

    BasicText(
        text = text,
        style = typography.xs.medium.center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Spacer(Modifier.height(12.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AppleDialogButton(
            text = cancelText,
            containerColor = palette.background2,
            contentColor = palette.text,
            modifier = Modifier.weight(1f),
            onClick = {
                onCancel()
                onDismiss()
            }
        )

        AppleDialogButton(
            text = confirmText,
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            modifier = Modifier.weight(1f),
            onClick = {
                onConfirm()
                onDismiss()
            }
        )
    }
}

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Dialog(onDismissRequest = onDismiss) {
    AppleDialogSurface(modifier = modifier, content = content)
}

@Composable
fun <T> ValueSelectorDialog(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: T,
    values: ImmutableList<T>,
    onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueText: @Composable (T) -> String = { it.toString() }
) = Dialog(onDismissRequest = onDismiss) {
    AppleDialogSurface(modifier = modifier) {
        ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = title,
            selectedValue = selectedValue,
            values = values,
            onValueSelect = onValueSelect,
            valueText = valueText
        )
    }
}

@Composable
fun <T> ValueSelectorDialogBody(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: T?,
    values: ImmutableList<T>,
    onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueText: @Composable (T) -> String = { it.toString() }
) = Column(modifier = modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = title,
        style = typography.s.copy(
            color = colorPalette.text,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        ),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )

    // weight with fill = false caps the list at whatever room the cancel button
    // leaves, so a long list scrolls instead of pushing the button off screen.
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
    ) {
        values.forEach { value ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .clickable(
                        onClick = {
                            onDismiss()
                            onValueSelect(value)
                        }
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                if (selectedValue == value) Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = colorPalette.accent,
                            shape = CircleShape
                        )
                ) {
                    drawCircle(
                        color = colorPalette.onAccent,
                        radius = 4.dp.toPx(),
                        center = size.center,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            blurRadius = 4.dp.toPx(),
                            offset = Offset(x = 0f, y = 1.dp.toPx())
                        )
                    )
                } else Spacer(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = 1.dp,
                            color = colorPalette.textDisabled,
                            shape = CircleShape
                        )
                )

                BasicText(
                    text = valueText(value),
                    style = typography.xs.medium
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    AppleDialogButton(
        text = stringResource(R.string.cancel),
        containerColor = colorPalette.background2,
        contentColor = colorPalette.text,
        modifier = Modifier.fillMaxWidth(),
        onClick = onDismiss
    )
}

@Suppress("ModifierMissing") // intentional, I guess
@Composable
fun ColumnScope.SliderDialogBody(
    provideState: @Composable () -> MutableState<Float>,
    onSlideComplete: (newState: Float) -> Unit,
    min: Float,
    max: Float,
    toDisplay: @Composable (Float) -> String = { it.toString() },
    @IntRange(from = 0) steps: Int = 0,
    label: String? = null
) {
    val (_, typography) = LocalAppearance.current
    var state by provideState()

    if (label != null) BasicText(
        text = label,
        style = typography.xs.semiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
    )

    Slider(
        state = state,
        setState = { state = it },
        onSlideComplete = { onSlideComplete(state) },
        range = min..max,
        steps = steps,
        modifier = Modifier
            .height(36.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )

    BasicText(
        text = toDisplay(state),
        style = typography.s.semiBold,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun SliderDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = { }
) = Dialog(onDismissRequest = onDismiss) {
    val (colorPalette, typography) = LocalAppearance.current

    AppleDialogSurface(modifier = modifier) {
        BasicText(
            text = title,
            style = typography.s.copy(
                color = colorPalette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )

        content()

        Spacer(Modifier.height(12.dp))

        AppleDialogButton(
            text = stringResource(R.string.confirm),
            containerColor = colorPalette.accent,
            contentColor = colorPalette.onAccent,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss
        )
    }
}

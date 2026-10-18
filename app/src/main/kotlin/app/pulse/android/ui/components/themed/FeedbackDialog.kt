package app.pulse.android.ui.components.themed

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import app.pulse.android.BuildConfig
import app.pulse.android.R
import app.pulse.android.utils.semiBold
import app.pulse.android.utils.toast
import app.pulse.core.ui.LocalAppearance
import app.pulse.providers.feedback.Feedback
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (palette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val isSending = remember { mutableStateOf(false) }

    val deviceId = remember {
        val prefs = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString("device_id", it) } }
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        BasicText(
            text = stringResource(R.string.feedback),
            style = typography.s.copy(
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Spacer(Modifier.height(12.dp))

        val nameShape = RoundedCornerShape(50.dp)
        TextField(
            value = name,
            onValueChange = { name = it },
            textStyle = typography.xs.semiBold,
            singleLine = true,
            hintText = stringResource(R.string.name),
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background2, nameShape)
                .border(1.dp, palette.text.copy(alpha = 0.12f), nameShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Spacer(Modifier.height(12.dp))

        val descriptionShape = RoundedCornerShape(50.dp)
        TextField(
            value = description,
            onValueChange = { description = it.take(2000) },
            textStyle = typography.xs.semiBold,
            maxLines = Int.MAX_VALUE,
            hintText = stringResource(R.string.description),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .background(palette.background2, descriptionShape)
                .border(1.dp, palette.text.copy(alpha = 0.12f), descriptionShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppleDialogButton(
                text = stringResource(R.string.close),
                containerColor = palette.background2,
                contentColor = palette.text,
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            AppleDialogButton(
                text = stringResource(R.string.send),
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank() && description.isNotBlank() && !isSending.value,
                onClick = {
                    isSending.value = true
                    scope.launch {
                        val status = withContext(Dispatchers.IO) {
                            Feedback.send(
                                BuildConfig.FEEDBACK_ENDPOINT,
                                name.trim(),
                                description,
                                BuildConfig.VERSION_NAME.substringBeforeLast("-"),
                                "${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
                                deviceId,
                            )
                        }
                        when {
                            status in 200..299 -> {
                                context.toast(context.getString(R.string.feedback_sent))
                                onDismiss()
                            }
                            status == 429 -> context.toast(context.getString(R.string.feedback_limited))
                            else -> context.toast(context.getString(R.string.feedback_failed))
                        }
                        isSending.value = false
                    }
                }
            )
        }
    }
}

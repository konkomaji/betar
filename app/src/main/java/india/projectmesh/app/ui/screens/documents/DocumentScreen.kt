package india.projectmesh.app.ui.screens.documents

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarShapes
import java.util.Locale

/**
 * DESIGN-BRIEF.md §9 "Documents": "Build one shared document template: a summary card at the
 * top in very plain words, then the detail below." Every document screen (privacy, terms,
 * safety, permissions, licenses, about, storm guide) is built on this one composable rather
 * than each hand-rolling its own layout.
 *
 * "each with a read aloud control" (§9): backed by the platform's own [TextToSpeech], not a
 * stub. Speaks [summary] plus every section body concatenated in order. Bengali readback quality
 * depends on whether the device has a Bengali TTS voice installed; if not, [TextToSpeech] simply
 * falls back to whatever voice it has, which is a real device-capability gap, not something this
 * screen can fix.
 */
data class DocumentSection(val title: String?, val body: String)

@Composable
fun DocumentScreen(
    title: String,
    summary: String,
    sections: List<DocumentSection>,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { }
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    val fullText = remember(summary, sections) {
        (listOf(summary) + sections.map { it.body }).joinToString(" ")
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val engine = tts ?: return@IconButton
                        if (speaking) {
                            engine.stop()
                            speaking = false
                        } else {
                            engine.language = Locale.getDefault()
                            engine.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "document-read-aloud")
                            speaking = true
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (speaking) context.getString(R.string.doc_stop_reading_button) else context.getString(R.string.doc_read_aloud_button)
                    },
                ) {
                    ReadAloudGlyph(speaking = speaking)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BetarShapes.large)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(20.dp),
            ) {
                Text(summary, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
        items(sections) { section ->
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                section.title?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                }
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        footer?.let { item { it() } }
    }
}

/**
 * Speaker glyph drawn directly with [Canvas] (a filled triangle plus two arcs when speaking,
 * just the triangle when stopped) so this control needs no icon-font/material-icons-extended
 * dependency this module doesn't already have.
 */
@Composable
private fun ReadAloudGlyph(speaking: Boolean, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.32f
        val cy = h * 0.5f
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.36f)
            lineTo(cx, h * 0.36f)
            lineTo(w * 0.56f, h * 0.14f)
            lineTo(w * 0.56f, h * 0.86f)
            lineTo(cx, h * 0.64f)
            lineTo(w * 0.12f, h * 0.64f)
            close()
        }
        drawPath(path, color = color)
        if (speaking) {
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.58f, h * 0.28f),
                size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.44f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.05f),
            )
        }
    }
}

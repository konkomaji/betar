package app.betar.comm.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.betar.comm.R
import kotlinx.coroutines.launch

/**
 * DESIGN-BRIEF.md §9 screen 2: three swipeable illustrated panels, eight words or fewer each,
 * with a read-aloud button. Copy is the exact three lines from DESIGN-BRIEF.md §9's own
 * onboarding description, not invented. The illustration itself is a placeholder block (this
 * pass has no illustration asset pipeline), left honestly blank rather than faked.
 *
 * Read-aloud is a stub: no TTS engine is wired up yet, so the button is present (per the
 * brief's accessibility requirement, "every icon has a spoken label") but does nothing on tap
 * beyond existing. A real implementation needs android.speech.tts.TextToSpeech wired to the
 * panel copy, not attempted this pass.
 */
@Composable
fun IntroPagerScreen(onFinished: () -> Unit) {
    val panels = listOf(R.string.onboarding_panel_1, R.string.onboarding_panel_2, R.string.onboarding_panel_3)
    val pagerState = rememberPagerState(pageCount = { panels.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.onboarding_skip),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onFinished() },
                style = MaterialTheme.typography.titleSmall,
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Illustration placeholder: no asset pipeline exists yet for these this pass.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(panels[page]),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { /* TODO: no TTS engine wired yet, see class doc */ }, modifier = Modifier.size(24.dp)) {
                        // No material-icons dependency in this project (out of this screen's
                        // scope to add); a plain triangle stands in for a play glyph.
                        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height / 2f)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xFF12608F))
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.onboarding_read_aloud), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(panels.size) { i ->
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(if (i == pagerState.currentPage) 26.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            IconButton(
                onClick = {
                    // Real bug fixed here: this used to only ever call onFinished() on the last
                    // page and do nothing at all on pages 1-2 (a CoroutineScope was needed for
                    // animateScrollToPage, punted in an earlier pass), so the "always enabled"
                    // next arrow the design calls for (Workflow Map FLOW 1) was silently dead on
                    // two of the three panels -- found via a real on-device tap that did nothing.
                    if (pagerState.currentPage == panels.lastIndex) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) {
                Text("→", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

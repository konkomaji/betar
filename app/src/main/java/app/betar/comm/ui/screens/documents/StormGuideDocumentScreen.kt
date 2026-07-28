package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/** DESIGN-BRIEF.md §9 screen 46, "illustrated, minimal text": plain Compose layout, no
 * illustrations were produced this pass (real gap, not attempted). */
@Composable
fun StormGuideDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_storm_guide_title),
        summary = stringResource(R.string.doc_storm_guide_summary),
        sections = listOf(
            DocumentSection(null, stringResource(R.string.doc_storm_guide_charge)),
            DocumentSection(null, stringResource(R.string.doc_storm_guide_relay)),
            DocumentSection(null, stringResource(R.string.doc_storm_guide_keep_open)),
            DocumentSection(null, stringResource(R.string.doc_storm_guide_send_alert)),
            DocumentSection(null, stringResource(R.string.doc_storm_guide_help_carry)),
        ),
    )
}

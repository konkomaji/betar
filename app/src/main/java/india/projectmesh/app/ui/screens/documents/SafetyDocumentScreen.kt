package india.projectmesh.app.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import india.projectmesh.app.R

/** DESIGN-BRIEF.md §9 screen 42, "the most important document here". Copy adapted from
 * docs/safety.html's real, published text. */
@Composable
fun SafetyDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_safety_title),
        summary = stringResource(R.string.doc_safety_summary),
        sections = listOf(
            DocumentSection(null, stringResource(R.string.doc_safety_not_replacement)),
            DocumentSection(stringResource(R.string.doc_safety_best_effort_title), stringResource(R.string.doc_safety_best_effort_body)),
            DocumentSection(null, stringResource(R.string.doc_safety_no_range_body)),
            DocumentSection(stringResource(R.string.doc_safety_no_review_title), stringResource(R.string.doc_safety_no_review_body)),
            DocumentSection(stringResource(R.string.doc_safety_alert_title), stringResource(R.string.doc_safety_alert_body)),
        ),
    )
}

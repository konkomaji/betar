package india.projectmesh.app.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import india.projectmesh.app.R

/** DESIGN-BRIEF.md §9 screen 41. */
@Composable
fun TermsDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_terms_title),
        summary = stringResource(R.string.doc_terms_summary),
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_terms_acceptable_use_title), stringResource(R.string.doc_terms_acceptable_use_body)),
            DocumentSection(stringResource(R.string.doc_terms_no_warranty_title), stringResource(R.string.doc_terms_no_warranty_body)),
            DocumentSection(stringResource(R.string.doc_terms_community_title), stringResource(R.string.doc_terms_community_body)),
            DocumentSection(stringResource(R.string.doc_terms_not_emergency_title), stringResource(R.string.doc_terms_not_emergency_body)),
        ),
    )
}

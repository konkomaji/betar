package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/** DESIGN-BRIEF.md §9 screen 40. Copy adapted from docs/privacy.html's real, published text. */
@Composable
fun PrivacyDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_privacy_title),
        summary = stringResource(R.string.doc_privacy_summary),
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_privacy_no_have_title), stringResource(R.string.doc_privacy_no_account)),
            DocumentSection(null, stringResource(R.string.doc_privacy_no_analytics)),
            DocumentSection(null, stringResource(R.string.doc_privacy_no_ads)),
            DocumentSection(null, stringResource(R.string.doc_privacy_no_internet)),
            DocumentSection(stringResource(R.string.doc_privacy_stored_title), stringResource(R.string.doc_privacy_stored_body)),
            DocumentSection(stringResource(R.string.doc_privacy_nearby_title), stringResource(R.string.doc_privacy_nearby_body)),
            DocumentSection(stringResource(R.string.doc_privacy_permissions_title), stringResource(R.string.doc_privacy_permissions_body)),
            DocumentSection(stringResource(R.string.doc_privacy_children_title), stringResource(R.string.doc_privacy_children_body)),
            DocumentSection(stringResource(R.string.doc_privacy_verify_title), stringResource(R.string.doc_privacy_verify_body)),
        ),
    )
}

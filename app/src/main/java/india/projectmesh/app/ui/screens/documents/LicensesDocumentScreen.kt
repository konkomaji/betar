package india.projectmesh.app.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import india.projectmesh.app.R

/** DESIGN-BRIEF.md §9 screen 44. Third-party list cross-checked against
 * android/app/build.gradle.kts's actual dependency block. */
@Composable
fun LicensesDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_licenses_title),
        summary = stringResource(R.string.doc_licenses_summary),
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_licenses_third_party_title), stringResource(R.string.doc_licenses_third_party_body)),
        ),
    )
}

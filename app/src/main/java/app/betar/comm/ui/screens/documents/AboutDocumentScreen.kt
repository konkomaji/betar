package app.betar.comm.ui.screens.documents

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betar.comm.R

/**
 * DESIGN-BRIEF.md §9 screen 45. Copy adapted from docs/about.html's real, published text
 * (name meaning, the Jagadish Chandra Bose tribute, Betar/Project Mesh relationship).
 *
 * Version string is a hard-coded literal matching android/app/build.gradle.kts's `versionName`
 * rather than `BuildConfig.VERSION_NAME`, because this module does not have
 * `buildFeatures.buildConfig = true` set, so that field is not generated. Real gap: this needs
 * updating by hand every time the version bumps, flagged here rather than silently risking a
 * stale value.
 */
private const val DISPLAYED_VERSION = "0.1.1-prealpha"
private const val REPO_URL = "https://github.com/konkomaji/project-mesh"

@Composable
fun AboutDocumentScreen() {
    val uriHandler = LocalUriHandler.current
    DocumentScreen(
        title = stringResource(R.string.doc_about_title),
        summary = stringResource(R.string.doc_about_summary),
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_about_name_title), stringResource(R.string.doc_about_name_body)),
            DocumentSection(null, stringResource(R.string.doc_about_bose_body)),
            DocumentSection(null, stringResource(R.string.doc_about_bose_claim_body)),
            DocumentSection(null, stringResource(R.string.doc_about_license_body)),
            DocumentSection(stringResource(R.string.doc_about_mesh_title), stringResource(R.string.doc_about_mesh_body)),
            DocumentSection(stringResource(R.string.doc_about_attribution_title), stringResource(R.string.doc_about_attribution_body)),
            DocumentSection(null, stringResource(R.string.doc_about_version, DISPLAYED_VERSION)),
        ),
        footer = {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { uriHandler.openUri(REPO_URL) }) {
                Text(stringResource(R.string.doc_about_repo_button))
            }
        },
    )
}

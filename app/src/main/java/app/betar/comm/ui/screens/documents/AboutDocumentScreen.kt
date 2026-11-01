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
 * Copy adapted from the betar repo's docs/about.html (name meaning, the Jagadish Chandra Bose
 * tribute, Betar/Project Mesh relationship) -- kept as-is, none of it is disaster-specific.
 *
 * Fixed 2026-11-01, two things:
 * 1. Version string was hard-coded to 0.1.1-prealpha, stale against `app/build.gradle.kts`'s
 *    real `versionName` (0.1.3-prealpha). Still a hard-coded literal, not `BuildConfig
 *    .VERSION_NAME` (this module lacks `buildFeatures.buildConfig = true`), same real gap as
 *    before, just corrected to the current value rather than left further stale.
 * 2. The attribution section said "no individual is named here, and none is named anywhere in
 *    the app" -- that was this project's standing rule before the pivot. The rule reversed by
 *    explicit instruction this session: every surface now credits Konko Maji by name. Replaced
 *    with local strings rather than editing strings.xml directly (a separate pass owns that
 *    file this session); `doc_about_attribution_title`/`doc_about_attribution_body` are no
 *    longer referenced by this screen.
 */
private const val DISPLAYED_VERSION = "0.1.3-prealpha"
private const val REPO_URL = "https://github.com/konkomaji/project-mesh"
private const val DEVELOPER_URL = "https://konkomaji.com/"

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
            DocumentSection("Developed by", "Betar is developed by Konko Maji (konkomaji.com). Translators and other contributors are credited as a group, not as named individuals."),
            DocumentSection(null, stringResource(R.string.doc_about_version, DISPLAYED_VERSION)),
        ),
        footer = {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { uriHandler.openUri(REPO_URL) }) {
                Text(stringResource(R.string.doc_about_repo_button))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { uriHandler.openUri(DEVELOPER_URL) }) {
                Text("konkomaji.com")
            }
        },
    )
}

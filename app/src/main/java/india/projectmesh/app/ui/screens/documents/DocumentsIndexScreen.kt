package india.projectmesh.app.ui.screens.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarShapes

/**
 * DESIGN-BRIEF.md §9 screen 39, "Documents index" (listed under the You section, but built here
 * alongside the documents it links to so both stay in sync). Whoever wires the real nav graph
 * (bottom-nav "You" tab -> this screen) should call this with real navigation callbacks; each
 * parameter defaults to a no-op only so this composable is previewable standalone.
 */
@Composable
fun DocumentsIndexScreen(
    onOpenPrivacy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    onOpenSafety: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenStormGuide: () -> Unit = {},
) {
    val rows = listOf(
        Triple(stringResource(R.string.doc_privacy_title), stringResource(R.string.documents_index_privacy_hint), onOpenPrivacy),
        Triple(stringResource(R.string.doc_terms_title), stringResource(R.string.documents_index_terms_hint), onOpenTerms),
        Triple(stringResource(R.string.doc_safety_title), stringResource(R.string.documents_index_safety_hint), onOpenSafety),
        Triple(stringResource(R.string.doc_permissions_title), stringResource(R.string.documents_index_permissions_hint), onOpenPermissions),
        Triple(stringResource(R.string.doc_licenses_title), stringResource(R.string.documents_index_licenses_hint), onOpenLicenses),
        Triple(stringResource(R.string.doc_about_title), stringResource(R.string.documents_index_about_hint), onOpenAbout),
        Triple(stringResource(R.string.doc_storm_guide_title), stringResource(R.string.documents_index_storm_guide_hint), onOpenStormGuide),
    )

    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Text(
                stringResource(R.string.documents_index_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        items(rows) { (label, hint, onClick) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(BetarShapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

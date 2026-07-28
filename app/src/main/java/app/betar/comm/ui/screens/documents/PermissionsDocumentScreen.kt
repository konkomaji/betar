package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/** DESIGN-BRIEF.md §9 screen 43. Permission list cross-checked against the actual
 * AndroidManifest.xml (Bluetooth, Wi-Fi Direct, location, notifications, battery exemption;
 * deliberately no INTERNET permission). */
@Composable
fun PermissionsDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_permissions_title),
        summary = stringResource(R.string.doc_permissions_summary),
        sections = listOf(
            DocumentSection(null, stringResource(R.string.doc_permissions_bluetooth)),
            DocumentSection(null, stringResource(R.string.doc_permissions_wifi_direct)),
            DocumentSection(null, stringResource(R.string.doc_permissions_location)),
            DocumentSection(null, stringResource(R.string.doc_permissions_notifications)),
            DocumentSection(null, stringResource(R.string.doc_permissions_battery)),
            DocumentSection(stringResource(R.string.doc_permissions_no_internet_title), stringResource(R.string.doc_permissions_no_internet_body)),
        ),
    )
}

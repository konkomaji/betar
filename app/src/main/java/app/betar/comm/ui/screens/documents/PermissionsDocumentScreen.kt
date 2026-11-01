package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/**
 * Permission list cross-checked against the actual AndroidManifest.xml (Bluetooth, Wi-Fi
 * Direct, location, notifications, battery exemption, camera for QR scanning).
 *
 * Fixed 2026-11-01: this used to claim "deliberately no INTERNET permission," true before the
 * pivot. It's no longer accurate: the Nostr relay bridge needs internet access to work at all.
 * **Real gap found while fixing this, outside this file's scope:** `AndroidManifest.xml` does
 * not actually request `android.permission.INTERNET` yet -- whoever wires the relay bridge's
 * networking needs to add it, this screen's copy is corrected ahead of that so it doesn't ship
 * saying the opposite of what the feature needs. Replaced with a local string rather than
 * editing strings.xml directly (a separate pass owns that file this session);
 * `doc_permissions_no_internet_title`/`doc_permissions_no_internet_body` are no longer
 * referenced by this screen.
 */
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
            DocumentSection(
                "Internet",
                "Betar uses internet access for one purpose only: bridging your local mesh traffic " +
                    "to and from the open Nostr relay network once any nearby device can reach it. " +
                    "It is never used to track you or send your data anywhere else.",
            ),
        ),
    )
}

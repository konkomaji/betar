package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/**
 * Copy adapted from the betar repo's docs/privacy.html.
 *
 * Fixed 2026-11-01: the summary and the "no internet" section both claimed Betar never
 * requests internet permission and nothing ever leaves the phones it travels between. Both
 * became false once the Nostr relay bridge shipped -- the app needs internet to bridge local
 * mesh content to and from the relay network, and content does reach it once bridged. Replaced
 * with accurate copy here rather than editing strings.xml directly (a separate pass owns that
 * file this session); the stale `doc_privacy_summary`/`doc_privacy_no_internet` string
 * resources are no longer referenced by this screen and can be removed or rewritten in that
 * pass.
 */
@Composable
fun PrivacyDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_privacy_title),
        summary = "Betar never asks for a phone number or an email address. There is no server, so " +
            "there are no server logs of who you talked to or what you posted. The app does use " +
            "the internet, but only to bridge your local mesh traffic to the open Nostr relay " +
            "network when any nearby device can reach it, never to send your data anywhere else.",
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_privacy_no_have_title), stringResource(R.string.doc_privacy_no_account)),
            DocumentSection(null, stringResource(R.string.doc_privacy_no_analytics)),
            DocumentSection(null, stringResource(R.string.doc_privacy_no_ads)),
            DocumentSection(
                "Internet use, stated plainly",
                "Betar requests internet permission for one reason only: bridging local mesh content " +
                    "to and from the open Nostr relay network once any nearby device reaches the " +
                    "internet. Nothing is sent to any other destination.",
            ),
            DocumentSection(stringResource(R.string.doc_privacy_stored_title), stringResource(R.string.doc_privacy_stored_body)),
            DocumentSection(stringResource(R.string.doc_privacy_nearby_title), stringResource(R.string.doc_privacy_nearby_body)),
            DocumentSection(stringResource(R.string.doc_privacy_permissions_title), stringResource(R.string.doc_privacy_permissions_body)),
            DocumentSection(stringResource(R.string.doc_privacy_children_title), stringResource(R.string.doc_privacy_children_body)),
            DocumentSection(
                "Location sharing",
                "Location is only ever shared when you tap the share button inside a specific chat, " +
                    "once, for that message alone. There is no continuous or background location " +
                    "sharing anywhere in Betar.",
            ),
            DocumentSection(stringResource(R.string.doc_privacy_verify_title), stringResource(R.string.doc_privacy_verify_body)),
        ),
    )
}

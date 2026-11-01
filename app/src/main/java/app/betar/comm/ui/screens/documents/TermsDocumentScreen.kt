package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/**
 * Fixed 2026-11-01: the summary and the "not an emergency service" section both dated from the
 * disaster-safety era, a disclaimer for a feature that no longer exists. Replaced with local
 * strings covering the open-source license and the mesh-relay agreement instead of editing
 * strings.xml directly (a separate pass owns that file this session); `doc_terms_summary` and
 * `doc_terms_not_emergency_*` are no longer referenced by this screen.
 */
@Composable
fun TermsDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_terms_title),
        summary = "Betar is free and open source, licensed under AGPL-3.0-or-later. Anyone can " +
            "read the code, change it, or run their own version. There is no company behind this " +
            "app and no account to agree to a contract with.",
        sections = listOf(
            DocumentSection(stringResource(R.string.doc_terms_acceptable_use_title), stringResource(R.string.doc_terms_acceptable_use_body)),
            DocumentSection(stringResource(R.string.doc_terms_no_warranty_title), stringResource(R.string.doc_terms_no_warranty_body)),
            DocumentSection(stringResource(R.string.doc_terms_community_title), stringResource(R.string.doc_terms_community_body)),
            DocumentSection(
                "Using Betar means relaying for others",
                "There is no server carrying messages, so other people's devices carry yours, and " +
                    "yours carries theirs. Using Betar means agreeing to help carry and relay other " +
                    "people's encrypted traffic as part of the mesh. You never see what you relay, " +
                    "it stays sealed end to end.",
            ),
        ),
    )
}

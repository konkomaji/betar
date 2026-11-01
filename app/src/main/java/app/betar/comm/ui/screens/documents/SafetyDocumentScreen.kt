package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.betar.comm.R

/**
 * "The most important document here." This is now the content-policy explanation, replacing
 * the old disaster-safety disclaimers. Copy adapted from the betar repo's docs/safety.html,
 * already rewritten for the pivot this session.
 *
 * `doc_safety_not_replacement` and `doc_safety_alert_*` (the old "not a replacement for
 * emergency services"/alert-feature sections) are no longer referenced here, that feature is
 * gone. Kept `doc_safety_best_effort_*`, `doc_safety_no_range_body`, and
 * `doc_safety_no_review_*` since they're still true and still matter: delivery is still
 * best-effort, mesh range is still physically limited, no independent security audit has
 * happened yet.
 */
@Composable
fun SafetyDocumentScreen() {
    DocumentScreen(
        title = stringResource(R.string.doc_safety_title),
        summary = "This is the most important page here. Read it before you rely on Betar for " +
            "anything real.",
        sections = listOf(
            DocumentSection(
                "Once it's out, it's out",
                "No one, including the people who built Betar, can delete a post or message once " +
                    "it has spread across the mesh or the relay network. There is no server to " +
                    "remove it from. Read this before you post anything you might later want to " +
                    "take back.",
            ),
            DocumentSection(
                "Community Notes, not a moderator",
                "Each person's own app can locally hide or warn on content flagged through " +
                    "Community Notes. This runs on your device only and never removes anything for " +
                    "anyone else. There is no central team deciding what counts as true, the " +
                    "scoring method is public and anyone can recompute it.",
            ),
            DocumentSection(stringResource(R.string.doc_safety_best_effort_title), stringResource(R.string.doc_safety_best_effort_body)),
            DocumentSection(null, stringResource(R.string.doc_safety_no_range_body)),
            DocumentSection(stringResource(R.string.doc_safety_no_review_title), stringResource(R.string.doc_safety_no_review_body)),
        ),
    )
}

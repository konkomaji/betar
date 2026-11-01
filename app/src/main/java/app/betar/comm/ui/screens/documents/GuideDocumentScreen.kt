package app.betar.comm.ui.screens.documents

import androidx.compose.runtime.Composable

/**
 * "How it works." New 2026-11-01, replacing `StormGuideDocumentScreen.kt` (deleted, 100%
 * disaster-prep content -- charge your phone, send an alert, help carry others -- none of it
 * reusable for this screen's purpose). Same `DocumentScreen` template every other document
 * screen uses. Local strings, not `R.string.*`: a separate pass owns strings.xml this session,
 * and this screen is entirely new copy with nothing existing to reuse.
 */
@Composable
fun GuideDocumentScreen() {
    DocumentScreen(
        title = "How it works",
        summary = "The one thing worth understanding before anything else: your phone talks " +
            "to nearby phones directly, nearby phones relay for each other, and the moment " +
            "anyone in that chain reaches the internet, it reaches everywhere else too.",
        sections = listOf(
            DocumentSection(
                "Local mesh",
                "Your phone talks directly to nearby phones over Bluetooth and Wi-Fi Direct. No " +
                    "internet needed for that. Messages hop from phone to phone to reach further " +
                    "than your own radio range, the same way a rumor spreads through a crowd.",
            ),
            DocumentSection(
                "Relay bridge",
                "The moment any one phone nearby reaches the internet, even briefly, it bridges " +
                    "everything to the open Nostr relay network and back. You never need your own " +
                    "internet connection, only someone near you, eventually, does. Kill one relay, " +
                    "another carries on, since no single relay is the only path.",
            ),
            DocumentSection(
                "Feed rooms",
                "Posts live in location-scoped rooms, block to country precision, not one giant " +
                    "global feed. Chronological only. Nothing is ranked or boosted by an " +
                    "algorithm.",
            ),
            DocumentSection(
                "Community Notes",
                "Anyone can attach a note to a post, backed by at least two source links and a " +
                    "short explanation. A public, shared scoring method decides which notes " +
                    "surface, not a moderator team, so no single person decides what counts as " +
                    "true.",
            ),
            DocumentSection(
                "Your ID",
                "A short code, for example A3F9K2, generated from your device's private key the " +
                    "first time you open Betar. It cannot be changed, and it is not tied to your " +
                    "name, phone number or email. You can also set a display name in Profile, 3 to " +
                    "8 characters, letters and digits plus . and _ only. It is a nickname only, " +
                    "shown next to your ID, and is not guaranteed to be unique to you.",
            ),
            DocumentSection(
                "Panic wipe",
                "Erases everything on this device and retires your ID for good. Opening Betar " +
                    "again after that starts over completely, with a brand new ID. The same thing " +
                    "happens if you uninstall and reinstall the app.",
            ),
        ),
    )
}

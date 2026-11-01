package app.betar.comm.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.ui.components.MeshRibbon
import app.betar.comm.ui.components.MeshRibbonState

/**
 * The app-wide shell: [MeshRibbon] pinned above everything, the four-tab bottom nav. No
 * global floating action button: "new post" (Feed) and "new chat" (Chats) are each that
 * screen's own FAB now, same as the locked Civic Calm mockup, since there's no single
 * always-available action every screen shares anymore now that the emergency button is gone.
 */
@Composable
fun BetarScaffold(
    current: BetarDestination,
    ribbonState: MeshRibbonState,
    onDestinationSelected: (BetarDestination) -> Unit,
    content: @Composable (padding: PaddingValues) -> Unit,
) {
    Scaffold(
        // .statusBarsPadding(): Scaffold does not automatically inset a plain composable
        // slotted into topBar (only TopAppBar handles that itself) -- without this the ribbon
        // renders underneath the system status bar, confirmed by a real on-device screenshot
        // where the clock overlapped the ribbon's text, not assumed from reading the code.
        topBar = { MeshRibbon(state = ribbonState, modifier = Modifier.statusBarsPadding().padding(12.dp)) },
        bottomBar = { BetarBottomNav(current = current, onSelected = onDestinationSelected) },
    ) { padding -> content(padding) }
}

@Composable
private fun BetarBottomNav(current: BetarDestination, onSelected: (BetarDestination) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BetarDestination.entries.forEach { dest ->
            val selected = dest == current
            Column(
                modifier = Modifier
                    .selectable(selected = selected, onClick = { onSelected(dest) })
                    .semantics { contentDescription = dest.label }
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(dest.icon)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(
                    dest.label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

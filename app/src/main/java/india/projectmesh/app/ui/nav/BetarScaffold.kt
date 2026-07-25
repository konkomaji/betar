package india.projectmesh.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import india.projectmesh.app.ui.components.MeshRibbon
import india.projectmesh.app.ui.components.MeshRibbonState

/**
 * The app-wide shell: [MeshRibbon] pinned above everything (DESIGN-BRIEF.md §8: "That ribbon is
 * how the core promise stays visible on every screen"), the five-tab bottom nav, and a
 * persistent emergency button ("never scrolls away and is never hidden behind a menu").
 */
@Composable
fun BetarScaffold(
    current: BetarDestination,
    ribbonState: MeshRibbonState,
    onDestinationSelected: (BetarDestination) -> Unit,
    onEmergencyClick: () -> Unit,
    content: @Composable (padding: PaddingValues) -> Unit,
) {
    Scaffold(
        // .statusBarsPadding(): Scaffold does not automatically inset a plain composable
        // slotted into topBar (only TopAppBar handles that itself) -- without this the ribbon
        // renders underneath the system status bar, confirmed by a real on-device screenshot
        // where the clock overlapped the ribbon's text, not assumed from reading the code.
        topBar = { MeshRibbon(state = ribbonState, modifier = Modifier.statusBarsPadding().padding(12.dp)) },
        bottomBar = { BetarBottomNav(current = current, onSelected = onDestinationSelected) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onEmergencyClick,
                containerColor = Color(0xFFC8102E),
                contentColor = Color.White,
                modifier = Modifier.semantics { contentDescription = "Emergency SOS" },
            ) {
                Text("SOS", fontWeight = FontWeight.ExtraBold)
            }
        },
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
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF5A6B77)),
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

package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betar.comm.R
import app.betar.comm.ui.components.CategoryTile
import app.betar.comm.ui.theme.BetarPolygonShapes

/** DESIGN-BRIEF.md §9 screen 8: "New conversation FAB menu: scan a code, join a group by name,
 * start a private group." Each path labelled, per §5.2's "every icon has a spoken label." */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationSheet(
    onDismiss: () -> Unit,
    onScanCode: () -> Unit,
    onJoinChannelByName: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.chats_new_conversation_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(
                    label = stringResource(R.string.chats_new_scan_code),
                    shape = BetarPolygonShapes.cookie9,
                    selected = false,
                    available = true,
                    onClick = { onDismiss(); onScanCode() },
                )
                CategoryTile(
                    label = stringResource(R.string.chats_new_join_channel),
                    shape = BetarPolygonShapes.hexagon,
                    selected = false,
                    available = true,
                    onClick = { onDismiss(); onJoinChannelByName() },
                )
                CategoryTile(
                    label = stringResource(R.string.chats_new_create_group),
                    shape = BetarPolygonShapes.clover4,
                    selected = false,
                    available = true,
                    onClick = { onDismiss(); onCreateGroup() },
                )
            }
        }
    }
}

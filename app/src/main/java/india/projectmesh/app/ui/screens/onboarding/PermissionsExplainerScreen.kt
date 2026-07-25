package india.projectmesh.app.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.requiredMeshPermissions
import india.projectmesh.app.ui.theme.BetarPolygonShapes

private data class PermissionRow(val titleRes: Int, val bodyRes: Int, val icon: Shape)

/**
 * DESIGN-BRIEF.md §9 screen 4: plain-language reasons before the system dialogs fire. The
 * actual grant uses [requiredMeshPermissions] (made internal in MainActivity.kt so this screen
 * reuses the exact same permission set the mesh coordinator needs, rather than a second,
 * possibly-drifting list).
 */
@Composable
fun PermissionsExplainerScreen(onDone: () -> Unit) {
    val rows = listOf(
        PermissionRow(R.string.onboarding_permission_nearby_title, R.string.onboarding_permission_nearby_body, BetarPolygonShapes.hexagon),
        PermissionRow(R.string.onboarding_permission_location_title, R.string.onboarding_permission_location_body, BetarPolygonShapes.clover4),
        PermissionRow(R.string.onboarding_permission_mic_title, R.string.onboarding_permission_mic_body, BetarPolygonShapes.scallop12),
        PermissionRow(R.string.onboarding_permission_notifications_title, R.string.onboarding_permission_notifications_body, BetarPolygonShapes.cookie9),
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Denied permissions are handled by MeshScreen itself on first "Start mesh" attempt
        // (it already shows mesh_permissions_denied and lets the user retry); onboarding just
        // moves forward either way rather than blocking install-to-first-message on a retry loop.
        onDone()
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(row.icon).background(MaterialTheme.colorScheme.secondaryContainer))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                    Column {
                        Text(stringResource(row.titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(row.bodyRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(india.projectmesh.app.ui.theme.LocalBetarExtendedColors.current.connectedContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_permission_no_internet_note),
                        color = india.projectmesh.app.ui.theme.LocalBetarExtendedColors.current.onConnectedContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Button(
            onClick = { permissionLauncher.launch(requiredMeshPermissions()) },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 14.dp),
        ) { Text(stringResource(R.string.onboarding_permissions_allow_button)) }
    }
}

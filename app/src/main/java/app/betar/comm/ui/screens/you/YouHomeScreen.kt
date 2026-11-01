package app.betar.comm.ui.screens.you

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.MeshApplication
import app.betar.comm.R
import app.betar.comm.ui.screens.onboarding.IdentityStore
import app.betar.comm.ui.theme.BetarPolygonShapes

/** Identity card plus the settings list. Pivoted 2026-11-01: Readiness and Carrying rows
 * (disaster-specific) are removed, not repurposed.
 *
 * Consolidated 2026-11-01 onto [IdentityStore] (`ui/screens/onboarding/IdentityStore.kt`),
 * the same store onboarding's identity step uses. This screen used to carry its own private,
 * separately-keyed nickname store, which could disagree with onboarding's about the user's
 * name depending on which screen last wrote it, a real bug flagged while building the
 * onboarding rework and left for this screen to fix. One store now, not two. */
@Composable
fun YouHomeScreen(
    onOpenLanguage: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDocuments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }
    var username by remember { mutableStateOf(IdentityStore.loadOrGenerate(context)) }

    // Computed here, in the composable function body, rather than inside the LazyColumn's
    // builder lambda: LazyListScope's content lambda is not itself @Composable (only item{}/
    // items{} content is), so stringResource() can't be called directly inside it.
    val rows = listOf(
        Triple(stringResource(R.string.you_row_language), BetarPolygonShapes.hexagon, onOpenLanguage),
        Triple(stringResource(R.string.you_row_appearance), BetarPolygonShapes.cookie9, onOpenAppearance),
        Triple(stringResource(R.string.you_row_privacy), BetarPolygonShapes.pentagon, onOpenPrivacy),
        Triple(stringResource(R.string.documents_index_title), BetarPolygonShapes.scallop12, onOpenDocuments),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IdentityCard(
                username = username,
                shortId = IdentityStore.shortIdFrom(app.identity.fingerprintHex()),
                fingerprintHex = app.identity.fingerprintHex(),
                safetyString = app.identity.safetyString(),
                onUsernameChange = { candidate ->
                    if (IdentityStore.isValidUsername(candidate)) {
                        username = candidate
                        if (candidate.isNotEmpty()) IdentityStore.save(context, candidate)
                    }
                },
            )
        }
        items(rows) { (label, shape, onClick) ->
            SettingsRow(label = label, shape = shape, onClick = onClick)
        }
    }
}

@Composable
private fun IdentityCard(
    username: String,
    shortId: String,
    fingerprintHex: String,
    safetyString: String,
    onUsernameChange: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value = username,
                onValueChange = onUsernameChange,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "· $shortId",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            "ID is permanent on this device, a fingerprint of your key, not a separately issued number.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            // Real scannable-code rendering (e.g. a QR of fingerprintHex) has no library wired
            // in yet -- this is a stated gap, not a fake code. The short verification code
            // below is real (app.identity.safetyString()).
            Text(stringResource(R.string.you_scannable_code_placeholder), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.you_verification_code_label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(safetyString, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        Button(onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("fingerprint", fingerprintHex))
        }) {
            Text(stringResource(R.string.you_copy_identity_button))
        }
    }
}

@Composable
private fun SettingsRow(label: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(26.dp).clip(shape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

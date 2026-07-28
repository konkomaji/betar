package app.betar.comm.ui.screens.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.R
import app.betar.comm.messaging.BulletinCategory
import app.betar.comm.messaging.BulletinMessenger
import app.betar.comm.messaging.BulletinPost
import app.betar.comm.messaging.ResourceCategory
import app.betar.comm.messaging.ResourceKind
import app.betar.comm.messaging.ResourceMessenger
import app.betar.comm.messaging.ResourcePost
import app.betar.comm.messaging.SosAlert
import app.betar.comm.messaging.SosCategory
import app.betar.comm.messaging.SosMessenger
import androidx.compose.foundation.clickable
import app.betar.comm.ui.components.CategoryTile
import app.betar.comm.ui.theme.BetarCategoryShapes
import app.betar.comm.ui.theme.BetarPolygonShapes
import app.betar.comm.ui.theme.PillShape
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 1000L
private val Amber = Color(0xFF9A6700)
private val AmberContainer = Color(0xFFF6EBD2)

/**
 * DESIGN-BRIEF.md §9 "Board" (screens 21-24): Alerts/Notices/Help behind one segmented control,
 * per design/Betar Board Map and Nearby.dc.html's `segmented(['Alerts','Notices','Help'], ...)`.
 * Wired to the real [SosMessenger]/[BulletinMessenger]/[ResourceMessenger] (`docs/FEATURES.md`
 * §1/§2/§4), not placeholder data.
 *
 * **Stated simplifications, not glossed over:** [BulletinPost]/[ResourcePost] carry no expiry
 * field yet, so notices show "posted N ago" instead of the mockup's amber expiry countdown
 * (there is no expiry data to count down). [SosAlert.location] exists but nothing populates it
 * yet, so alert cards omit distance/direction rather than fabricating it. Acknowledgement is a
 * real boolean per alert, not the count the mockup implies; the wire format only tracks whether
 * any acknowledgement broadcast for that alert id was seen, not how many.
 */
@Composable
fun BoardScreen(
    sosMessenger: SosMessenger,
    bulletinMessenger: BulletinMessenger,
    resourceMessenger: ResourceMessenger,
) {
    var tab by remember { mutableIntStateOf(0) }
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            sosMessenger.pollForNewAlerts()
            bulletinMessenger.pollForNewPosts()
            resourceMessenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    if (composing) {
        ComposeNoticeOrHelpScreen(
            onClose = { composing = false },
            onSubmit = { category, text ->
                bulletinMessenger.send(BulletinCategory.entries.getOrElse(category) { BulletinCategory.OTHER }, text)
                composing = false
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp, 12.dp, 12.dp, 8.dp)) {
            BoardSegmentedControl(
                items = listOf(
                    stringResource(R.string.board_tab_alerts),
                    stringResource(R.string.board_tab_notices),
                    stringResource(R.string.board_tab_help),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        when (tab) {
            0 -> AlertsTab(sosMessenger)
            1 -> NoticesTab(bulletinMessenger)
            else -> HelpTab(resourceMessenger, onCompose = { composing = true })
        }
    }
}

@Composable
private fun BoardSegmentedControl(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier.clip(MaterialTheme.shapes.extraLarge),
                )
            }
        }
    }
}

@Composable
private fun AlertsTab(messenger: SosMessenger) {
    if (messenger.alerts.isEmpty()) {
        BoardEmptyState(
            title = stringResource(R.string.board_alerts_empty_title),
            body = stringResource(R.string.board_alerts_empty_body),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(messenger.alerts) { alert -> AlertCard(alert, messenger) }
    }
}

@Composable
private fun AlertCard(alert: SosAlert, messenger: SosMessenger) {
    val acknowledged = messenger.acknowledgedIdHexes.contains(alert.idHex)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Color(0xFFC8102E)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryGlyph(shapeFor(alert.category), Color.White, Color(0xFF8C0B20), size = 44.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(alert.category.labelRes), color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        }
        if (alert.text.isNotBlank()) {
            Text(alert.text, color = Color(0xFFFFEEF1), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            relativeTime(alert.timestampSeconds),
            color = Color(0xFFF6C9D0),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(if (acknowledged) R.string.sos_acknowledged else R.string.sos_not_acknowledged),
            color = Color(0xFFF6C9D0),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { if (!acknowledged) messenger.acknowledge(alert) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF8C0B20)),
        ) {
            Text(stringResource(if (acknowledged) R.string.sos_acknowledged else R.string.board_i_am_coming_to_help), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoticesTab(messenger: BulletinMessenger) {
    if (messenger.posts.isEmpty()) {
        BoardEmptyState(title = stringResource(R.string.board_notices_empty_title), body = stringResource(R.string.board_notices_empty_body))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(messenger.posts) { post -> NoticeCard(post) }
    }
}

@Composable
private fun NoticeCard(post: BulletinPost) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CategoryGlyph(shapeFor(post.category), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer, size = 52.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(post.category.labelRes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(post.text, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium)
            Box(
                Modifier.padding(top = 8.dp).clip(MaterialTheme.shapes.extraLarge).background(AmberContainer).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(relativeTime(post.timestampSeconds), color = Color(0xFF7A5100), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HelpTab(messenger: ResourceMessenger, onCompose: () -> Unit) {
    var kind by remember { mutableStateOf(ResourceKind.HAVE) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
            ResourceKind.entries.forEach { k ->
                val selected = k == kind
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { kind = k },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(k.labelRes),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        val filtered = messenger.posts.filter { it.kind == kind }
        if (filtered.isEmpty()) {
            BoardEmptyState(title = stringResource(R.string.board_help_empty_title), body = stringResource(R.string.board_help_empty_body))
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered) { post -> HelpCard(post) }
            }
        }
        Button(
            onClick = onCompose,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.board_put_up_notice), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HelpCard(post: ResourcePost) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryGlyph(shapeFor(post.category), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer, size = 48.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(post.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                "${stringResource(post.category.labelRes)} · ${relativeTime(post.timestampSeconds)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ComposeNoticeOrHelpScreen(onClose: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.board_put_up_notice), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("✕", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
        }
        Text(stringResource(R.string.board_pick_picture_first), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(240.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(BulletinCategory.entries) { c ->
                CategoryTile(
                    label = stringResource(c.labelRes),
                    shape = shapeFor(c),
                    selected = c.code == selected,
                    available = true,
                    onClick = { selected = c.code },
                    modifier = Modifier.height(96.dp),
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text(stringResource(R.string.board_add_words_optional)) },
            keyboardOptions = KeyboardOptions.Default,
        )
        Button(
            onClick = { if (text.isNotBlank()) onSubmit(selected, text) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(60.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.board_put_it_up), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BoardEmptyState(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CategoryGlyph(shape: Shape, fill: Color, dashColor: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(shape).background(fill))
}

private fun shapeFor(category: SosCategory): Shape = when (category) {
    SosCategory.MEDICAL -> BetarCategoryShapes.medical
    SosCategory.TRAPPED -> BetarCategoryShapes.trapped
    SosCategory.FIRE -> BetarCategoryShapes.fire
    SosCategory.VIOLENCE -> BetarCategoryShapes.danger
    SosCategory.OTHER -> BetarCategoryShapes.other
}

private fun shapeFor(category: BulletinCategory): Shape = when (category) {
    BulletinCategory.RELIEF_CAMP -> BetarPolygonShapes.hexagon
    BulletinCategory.FOOD -> BetarPolygonShapes.hexagon
    BulletinCategory.WATER -> BetarPolygonShapes.scallop12
    BulletinCategory.MEDICINE -> BetarPolygonShapes.clover4
    BulletinCategory.ROAD_STATUS -> PillShape
    BulletinCategory.SHELTER -> BetarPolygonShapes.pentagon
    BulletinCategory.MISSING_PERSON -> BetarPolygonShapes.diamond
    BulletinCategory.OTHER -> BetarPolygonShapes.cookie9
}

private fun shapeFor(category: ResourceCategory): Shape = when (category) {
    ResourceCategory.FOOD -> BetarPolygonShapes.hexagon
    ResourceCategory.SHELTER -> BetarPolygonShapes.pentagon
    ResourceCategory.TRANSPORT -> PillShape
    ResourceCategory.TOOLS -> BetarPolygonShapes.flower6
    ResourceCategory.BLOOD_DONOR -> BetarPolygonShapes.clover4
    ResourceCategory.CHARGING -> BetarPolygonShapes.burst8
    ResourceCategory.LABOUR -> BetarPolygonShapes.scallop12
    ResourceCategory.OTHER -> BetarPolygonShapes.cookie9
}

/** No relative-time formatting utility existed anywhere in the app before this; kept local and
 * small rather than pulling in a date library for one string. Composable (not a plain function)
 * so it can go through stringResource() like every other user-facing string in this screen. */
@Composable
private fun relativeTime(epochSeconds: Long): String {
    val deltaSeconds = (System.currentTimeMillis() / 1000L) - epochSeconds
    val minutes = deltaSeconds / 60
    return when {
        minutes < 1 -> stringResource(R.string.board_time_just_now)
        minutes < 60 -> stringResource(R.string.board_time_minutes_ago, minutes)
        minutes < 24 * 60 -> stringResource(R.string.board_time_hours_ago, minutes / 60)
        else -> stringResource(R.string.board_time_days_ago, minutes / (24 * 60))
    }
}

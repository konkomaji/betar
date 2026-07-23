package india.projectmesh.app.messaging

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 1000L

@Composable
fun SosScreen(messenger: SosMessenger) {
    var category by remember { mutableStateOf(SosCategory.MEDICAL) }
    var draft by remember { mutableStateOf("") }
    val selectedFormat = stringResource(R.string.category_selected)
    val postFormat = stringResource(R.string.post_prefixed_category)

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewAlerts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sos_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sos_subtitle), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in SosCategory.entries) {
                val label = stringResource(c.labelRes)
                Button(onClick = { category = c }) {
                    Text(if (c == category) selectedFormat.format(label) else label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.sos_whats_happening_label)) },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(category, draft)
                    draft = ""
                }
            }) {
                Text(stringResource(R.string.sos_send_button))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.alerts) { alert ->
                val acked = messenger.acknowledgedIdHexes.contains(alert.idHex)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(postFormat.format(stringResource(alert.category.labelRes), alert.text), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (acked) R.string.sos_acknowledged else R.string.sos_not_acknowledged),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!acked) {
                            Button(onClick = { messenger.acknowledge(alert) }) { Text(stringResource(R.string.action_acknowledge)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BulletinScreen(messenger: BulletinMessenger) {
    var category by remember { mutableStateOf(BulletinCategory.RELIEF_CAMP) }
    var draft by remember { mutableStateOf("") }
    val selectedFormat = stringResource(R.string.category_selected)
    val postFormat = stringResource(R.string.post_prefixed_category)

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.bulletin_title), style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in BulletinCategory.entries) {
                val label = stringResource(c.labelRes)
                Button(onClick = { category = c }) {
                    Text(if (c == category) selectedFormat.format(label) else label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.bulletin_label)) },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(category, draft)
                    draft = ""
                }
            }) {
                Text(stringResource(R.string.action_post))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.posts) { post ->
                Text(postFormat.format(stringResource(post.category.labelRes), post.text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ResourceScreen(messenger: ResourceMessenger) {
    var kind by remember { mutableStateOf(ResourceKind.HAVE) }
    var category by remember { mutableStateOf(ResourceCategory.FOOD) }
    var draft by remember { mutableStateOf("") }
    val selectedFormat = stringResource(R.string.category_selected)
    val postFormat = stringResource(R.string.resource_post_prefixed)

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.resource_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in ResourceKind.entries) {
                val label = stringResource(k.labelRes)
                Button(onClick = { kind = k }) {
                    Text(if (k == kind) selectedFormat.format(label) else label)
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in ResourceCategory.entries) {
                val label = stringResource(c.labelRes)
                Button(onClick = { category = c }) {
                    Text(if (c == category) selectedFormat.format(label) else label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.resource_details_label)) },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(kind, category, draft)
                    draft = ""
                }
            }) {
                Text(stringResource(R.string.action_post))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.posts) { post ->
                Text(
                    postFormat.format(stringResource(post.kind.labelRes), stringResource(post.category.labelRes), post.text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

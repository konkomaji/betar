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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 1000L

@Composable
fun SosScreen(messenger: SosMessenger) {
    var category by remember { mutableStateOf(SosCategory.MEDICAL) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewAlerts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Emergency SOS", style = MaterialTheme.typography.titleMedium)
        Text(
            "One-tap high-priority broadcast -- no device location this pass, see class doc.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in SosCategory.entries) {
                Button(onClick = { category = c }) {
                    Text(if (c == category) "[${c.label}]" else c.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("What's happening") },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(category, draft)
                    draft = ""
                }
            }) {
                Text("Send SOS")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.alerts) { alert ->
                val acked = messenger.acknowledgedIdHexes.contains(alert.idHex)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("[${alert.category.label}] ${alert.text}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (acked) "Acknowledged" else "Not yet acknowledged", style = MaterialTheme.typography.bodySmall)
                        if (!acked) {
                            Button(onClick = { messenger.acknowledge(alert) }) { Text("Acknowledge") }
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

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Disaster bulletin board", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in BulletinCategory.entries) {
                Button(onClick = { category = c }) {
                    Text(if (c == category) "[${c.label}]" else c.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Bulletin") },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(category, draft)
                    draft = ""
                }
            }) {
                Text("Post")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.posts) { post ->
                Text("[${post.category.label}] ${post.text}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ResourceScreen(messenger: ResourceMessenger) {
    var kind by remember { mutableStateOf(ResourceKind.HAVE) }
    var category by remember { mutableStateOf(ResourceCategory.FOOD) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Community resource board", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in ResourceKind.entries) {
                Button(onClick = { kind = k }) {
                    Text(if (k == kind) "[${k.label}]" else k.label)
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in ResourceCategory.entries) {
                Button(onClick = { category = c }) {
                    Text(if (c == category) "[${c.label}]" else c.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Details") },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(kind, category, draft)
                    draft = ""
                }
            }) {
                Text("Post")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(messenger.posts) { post ->
                Text("[${post.kind.label}/${post.category.label}] ${post.text}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

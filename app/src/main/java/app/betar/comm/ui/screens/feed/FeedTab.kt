package app.betar.comm.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.messaging.FeedMessenger
import app.betar.comm.messaging.FeedPost
import app.betar.comm.messaging.NotesMessenger
import app.betar.comm.messaging.RoomPrecision
import app.betar.comm.ui.theme.BetarShapes
import kotlinx.coroutines.delay

private const val POST_MAX_LENGTH = 280
private const val EXPLANATION_MAX_LENGTH = 150
private const val POLL_INTERVAL_MS = 1000L

private sealed class FeedRoute {
    object List : FeedRoute()
    object NewPost : FeedRoute()
    data class Detail(val postId: String) : FeedRoute()
}

private fun roomLabel(precision: Int): String = when (precision) {
    RoomPrecision.BLOCK -> "#block"
    RoomPrecision.CITY -> "#city"
    RoomPrecision.REGION -> "#region"
    else -> "#country"
}

/**
 * Feed tab: geohash-scoped rooms, reverse-chronological, no ranking algorithm, matching the
 * locked Civic Calm design. Replaces the cut disaster bulletin board's tab slot.
 */
@Composable
fun FeedTab(feedMessenger: FeedMessenger, notesMessenger: NotesMessenger) {
    var route by rememberSaveable { mutableStateOf<FeedRoute>(FeedRoute.List) }
    var activeRoom by rememberSaveable { mutableStateOf(RoomPrecision.BLOCK) }

    LaunchedEffect(Unit) {
        while (true) {
            feedMessenger.pollForNewPosts()
            notesMessenger.pollForNewNotes()
            delay(POLL_INTERVAL_MS)
        }
    }

    when (val current = route) {
        is FeedRoute.List -> FeedListScreen(
            posts = feedMessenger.posts,
            activeRoom = activeRoom,
            onRoomSelected = { activeRoom = it },
            onOpenPost = { post -> route = FeedRoute.Detail(post.idHex) },
            onNewPost = { route = FeedRoute.NewPost },
        )
        is FeedRoute.NewPost -> NewPostScreen(
            defaultRoom = activeRoom,
            onPost = { text, room ->
                if (feedMessenger.post(text, room)) route = FeedRoute.List
            },
            onCancel = { route = FeedRoute.List },
        )
        is FeedRoute.Detail -> {
            val post = feedMessenger.posts.find { it.idHex == current.postId }
            if (post == null) {
                route = FeedRoute.List
            } else {
                PostDetailScreen(
                    post = post,
                    notes = notesMessenger.notesFor(post.idHex),
                    onAddNote = { link1, link2, explanation ->
                        notesMessenger.addNote(post.idHex, link1, link2, explanation)
                    },
                    onBack = { route = FeedRoute.List },
                )
            }
        }
    }
}

@Composable
private fun FeedListScreen(
    posts: List<FeedPost>,
    activeRoom: Int,
    onRoomSelected: (Int) -> Unit,
    onOpenPost: (FeedPost) -> Unit,
    onNewPost: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(RoomPrecision.BLOCK, RoomPrecision.CITY, RoomPrecision.REGION, RoomPrecision.COUNTRY)
                    .forEach { precision ->
                        val selected = precision == activeRoom
                        Text(
                            roomLabel(precision),
                            modifier = Modifier
                                .clip(BetarShapes.large)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onRoomSelected(precision) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
            }
            val roomPosts = posts.filter { it.roomPrecision == activeRoom }
            if (roomPosts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing posted in this room yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(roomPosts) { post ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(BetarShapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onOpenPost(post) }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(post.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "via mesh",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewPost,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun NewPostScreen(defaultRoom: Int, onPost: (String, Int) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf(defaultRoom) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New post", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RoomPrecision.BLOCK, RoomPrecision.CITY, RoomPrecision.REGION, RoomPrecision.COUNTRY)
                .forEach { precision ->
                    val selected = precision == room
                    Text(
                        roomLabel(precision),
                        modifier = Modifier
                            .clip(BetarShapes.large)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { room = precision }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= POST_MAX_LENGTH) text = it },
            placeholder = { Text("What's happening") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Text(
            "${text.length} / $POST_MAX_LENGTH",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            "Image attachments are not available yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPost(text, room) }, enabled = text.isNotEmpty() && text.length <= POST_MAX_LENGTH) {
                Text("Post")
            }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun PostDetailScreen(
    post: FeedPost,
    notes: List<app.betar.comm.messaging.CommunityNote>,
    onAddNote: (String, String, String) -> Boolean,
    onBack: () -> Unit,
) {
    var link1 by rememberSaveable { mutableStateOf("") }
    var link2 by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("← Back", Modifier.clickable(onClick = onBack), color = MaterialTheme.colorScheme.primary)
        Text(post.text, style = MaterialTheme.typography.bodyLarge)

        if (notes.isNotEmpty()) {
            Text("Community notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            notes.forEach { note ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(BetarShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(note.explanation, style = MaterialTheme.typography.bodyMedium)
                    Text(note.link1, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(note.link2, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Text("Add a community note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Needs 2 source links and a short explanation. No single moderator decides what counts as true, a public scoring method does, not yet built.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        OutlinedTextField(value = link1, onValueChange = { link1 = it }, placeholder = { Text("Source link 1") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = link2, onValueChange = { link2 = it }, placeholder = { Text("Source link 2") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = explanation,
            onValueChange = { if (it.length <= EXPLANATION_MAX_LENGTH) explanation = it },
            placeholder = { Text("Explain, in your own words") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Text(
            "${explanation.length} / $EXPLANATION_MAX_LENGTH",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Button(
            onClick = {
                if (onAddNote(link1, link2, explanation)) {
                    link1 = ""; link2 = ""; explanation = ""
                }
            },
            enabled = link1.isNotBlank() && link2.isNotBlank() && explanation.isNotEmpty() && explanation.length <= EXPLANATION_MAX_LENGTH,
        ) { Text("Submit note") }
    }
}

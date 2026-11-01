package app.betar.comm.ui.screens.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.messaging.CommunityNote
import app.betar.comm.messaging.ContentOrigin
import app.betar.comm.messaging.FeedMessenger
import app.betar.comm.messaging.FeedPost
import app.betar.comm.messaging.NOTE_CONFIRMED_THRESHOLD
import app.betar.comm.messaging.NotesMessenger
import app.betar.comm.messaging.Rating
import app.betar.comm.messaging.RatingsMessenger
import app.betar.comm.messaging.RoomPrecision
import app.betar.comm.messaging.communityScore
import app.betar.comm.nostr.RelayBridge
import app.betar.comm.ui.theme.BetarShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private const val POST_MAX_LENGTH = 280
private const val EXPLANATION_MAX_LENGTH = 150
private const val POLL_INTERVAL_MS = 1000L
// Budget, not a hard cap: the loop below stops at the first JPEG quality step at or under this,
// or gives up at MIN_QUALITY and ships whatever that produced. Chosen in the same spirit as
// core/src/crypto/padding.rs's size-bucket reasoning for this project's low-end-device target
// (docs/LOCALIZATION-UX.md's "inexpensive Android phones"): small enough that a thumbnail
// doesn't dominate an envelope's cost on a slow BLE link, generous enough to still look like a
// real photo rather than a mud smear.
private const val THUMBNAIL_TARGET_BYTES = 20_000
private const val THUMBNAIL_MAX_DIMENSION = 320
private const val JPEG_MIN_QUALITY = 30

/** Decodes [uri] downsampled (never loads the full-resolution bitmap into memory, this project
 *  targets ~2GB-RAM phones), then re-encodes as JPEG, stepping quality down until the result
 *  fits [THUMBNAIL_TARGET_BYTES] or hits [JPEG_MIN_QUALITY]. Re-encoding through [Bitmap] does
 *  not carry EXIF through: `BitmapFactory` decodes pixel data only, and `Bitmap.compress`
 *  writes a fresh JPEG from those pixels with no metadata segment, confirmed by the fact that
 *  `ExifInterface` is never invoked anywhere in this path, there's no metadata left for it to
 *  copy even if it were. Returns null if the image can't be decoded. */
private fun decodeAndCompressThumbnail(context: android.content.Context, uri: Uri): ByteArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    // `||`, not `&&`: THUMBNAIL_MAX_DIMENSION is an upper bound on the LARGER side, so
    // downsampling must continue until BOTH sides are under it. With `&&`, an extreme
    // aspect-ratio image (e.g. a 4000x200 panorama) stops downsampling the moment the
    // already-small side crosses the threshold, leaving the long side undersampled and
    // defeating the "never loads the full-resolution bitmap into memory" guarantee this
    // function's own doc comment makes.
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION || bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

    var quality = 85
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 15
    } while (bytes.size > THUMBNAIL_TARGET_BYTES && quality >= JPEG_MIN_QUALITY)
    bitmap.recycle()
    return bytes
}

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
fun FeedTab(feedMessenger: FeedMessenger, notesMessenger: NotesMessenger, relayBridge: RelayBridge, ratingsMessenger: RatingsMessenger) {
    var route by rememberSaveable { mutableStateOf<FeedRoute>(FeedRoute.List) }
    var activeRoom by rememberSaveable { mutableStateOf(RoomPrecision.BLOCK) }

    LaunchedEffect(Unit) {
        while (true) {
            feedMessenger.pollForNewPosts()
            notesMessenger.pollForNewNotes()
            relayBridge.tick()
            ratingsMessenger.pollForNewRatings()
            delay(POLL_INTERVAL_MS)
        }
    }

    /** True if any note on [postId] has reached [NOTE_CONFIRMED_THRESHOLD] net helpful votes,
     *  the client-side content-policy filter decided earlier this session: no server or relay
     *  can delete anything, but a confirmed note can make each person's own client warn on the
     *  post it's attached to, tap-to-reveal rather than hidden outright. */
    fun isFlagged(postId: String): Boolean =
        notesMessenger.notesFor(postId).any { communityScore(it.idHex, ratingsMessenger.ratings) >= NOTE_CONFIRMED_THRESHOLD }

    when (val current = route) {
        is FeedRoute.List -> FeedListScreen(
            posts = feedMessenger.posts,
            activeRoom = activeRoom,
            onRoomSelected = { activeRoom = it },
            onOpenPost = { post -> route = FeedRoute.Detail(post.idHex) },
            onNewPost = { route = FeedRoute.NewPost },
            isFlagged = ::isFlagged,
        )
        is FeedRoute.NewPost -> NewPostScreen(
            defaultRoom = activeRoom,
            onPost = { text, room, thumbnail ->
                if (feedMessenger.post(text, room, thumbnail)) route = FeedRoute.List
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
                    ratings = ratingsMessenger.ratings,
                    isFlagged = isFlagged(post.idHex),
                    onAddNote = { link1, link2, explanation ->
                        notesMessenger.addNote(post.idHex, link1, link2, explanation)
                    },
                    onRate = { noteId, helpful -> ratingsMessenger.rate(noteId, helpful) },
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
    isFlagged: (String) -> Boolean,
) {
    var revealed by rememberSaveable { mutableStateOf(setOf<String>()) }
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
                        val flagged = isFlagged(post.idHex) && post.idHex !in revealed
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(BetarShapes.medium)
                                .background(if (flagged) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    if (flagged) revealed = revealed + post.idHex else onOpenPost(post)
                                }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (flagged) {
                                Text(
                                    "A confirmed Community Note flags this post. Tap to show it.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text(post.text, style = MaterialTheme.typography.bodyLarge)
                                post.thumbnailBytes?.let { bytes ->
                                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(BetarShapes.small),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                                Text(
                                    if (post.origin == app.betar.comm.messaging.ContentOrigin.Mesh) "via mesh" else "via relay",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
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
private fun NewPostScreen(defaultRoom: Int, onPost: (String, Int, ByteArray?) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf(defaultRoom) }
    var geohash by rememberSaveable { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                thumbnailBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeAndCompressThumbnail(context, uri)
                }
            }
        }
    }

    fun locate() {
        locating = true
        scope.launch {
            val location = app.betar.comm.location.LocationHelper.getOneShotLocation(context)
            if (location != null) {
                // Derive the geohash at a length matching the room tier the user already has
                // selected (the chip row above), rather than always requesting full (block-level)
                // precision: a fixed precision=7 request made `geohashLengthToRoomPrecision`
                // always resolve to BLOCK, silently overriding a user's CITY/REGION/COUNTRY
                // choice with the most location-revealing tier every time "Use my location" was
                // pressed -- a real privacy regression, not just a dead-code smell.
                val geohashPrecisionForRoom = when (room) {
                    RoomPrecision.BLOCK -> 7
                    RoomPrecision.CITY -> 5
                    RoomPrecision.REGION -> 3
                    else -> 1
                }
                geohash = app.betar.comm.location.encodeGeohash(location.latitude, location.longitude, precision = geohashPrecisionForRoom)
            }
            locating = false
        }
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) locate() }

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
                            .clickable { room = precision; geohash = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) locate() else locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                },
                enabled = !locating,
            ) { Text(if (locating) "Locating..." else "Use my location") }
            if (geohash != null) {
                Text(
                    "${roomLabel(room)} (#$geohash)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
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
        if (thumbnailBytes == null) {
            Button(onClick = {
                imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Add image") }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val bmp = remember(thumbnailBytes) {
                    thumbnailBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).clip(BetarShapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mesh preview, full image once a relay is reached",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    "Remove image",
                    modifier = Modifier.clickable { thumbnailBytes = null },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPost(text, room, thumbnailBytes) }, enabled = text.isNotEmpty() && text.length <= POST_MAX_LENGTH) {
                Text("Post")
            }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun PostDetailScreen(
    post: FeedPost,
    notes: List<CommunityNote>,
    ratings: List<Rating>,
    isFlagged: Boolean,
    onAddNote: (String, String, String) -> Boolean,
    onRate: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var link1 by rememberSaveable { mutableStateOf("") }
    var link2 by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }
    var revealedHere by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("← Back", Modifier.clickable(onClick = onBack), color = MaterialTheme.colorScheme.primary)

        if (isFlagged && !revealedHere) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(BetarShapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { revealedHere = true }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "A confirmed Community Note flags this post.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "No server or relay can remove it, this device is only hiding it locally. Tap to show it anyway.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Text(post.text, style = MaterialTheme.typography.bodyLarge)
            post.thumbnailBytes?.let { bytes ->
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bmp != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(BetarShapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            "Mesh preview, full image once a relay is reached",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        if (notes.isNotEmpty()) {
            Text("Community notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            notes.forEach { note ->
                val score = communityScore(note.idHex, ratings)
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
                    Text(
                        "Net helpful: $score${if (score >= NOTE_CONFIRMED_THRESHOLD) " · confirmed" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRate(note.idHex, true) }) { Text("Helpful") }
                        Button(onClick = { onRate(note.idHex, false) }) { Text("Not helpful") }
                    }
                }
            }
        }

        Text("Add a community note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Needs 2 source links and a short explanation. No single moderator decides what counts as true, a public helpful-vote score does.",
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

package app.betar.comm.nostr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "NostrRelayClient"

/** A small set of well-known, long-standing public Nostr relays, not operated by this project.
 *  Deliberately more than one: the whole point of bridging to an open relay network instead of
 *  running a server is that killing any one relay does not kill reach, so this client never
 *  depends on exactly one being up. */
private val DEFAULT_RELAYS = listOf(
    "wss://relay.damus.io",
    "wss://nos.lol",
    "wss://relay.nostr.band",
)

/** A filter for a `REQ` subscription, NIP-01's filter object. Only the fields Betar actually
 *  uses are modeled; NIP-01 defines more (`ids`, `authors`, `since`, `until`, `limit`) that this
 *  client does not need yet. */
data class NostrFilter(val kinds: List<Int>, val tagFilters: Map<String, List<String>> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kinds", JSONArray(kinds))
        tagFilters.forEach { (tagName, values) -> put("#$tagName", JSONArray(values)) }
    }
}

/**
 * WebSocket connection to one relay, speaking the client-to-relay and relay-to-client message
 * patterns NIP-01 §"Communication between clients and relays" defines exactly (`EVENT`, `REQ`,
 * `CLOSE` outbound; `EVENT`, `OK`, `EOSE`, `CLOSED`, `NOTICE` inbound), read directly from the
 * spec before writing this, not reconstructed from memory.
 *
 * **Not done, stated plainly:** no reconnect/backoff strategy, a dropped connection stays
 * dropped until [RelayBridge] notices and reconnects. No `since`/`until`/`limit` filter support.
 * No NIP-11 relay-information-document fetch to check a relay's actual policies before using it.
 * These are real gaps for a production client, not attempted this pass.
 */
class NostrRelayConnection(private val relayUrl: String, private val onEvent: (subId: String, event: NostrEvent) -> Unit) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    // [RelayBridge.tick] calls [connect] again every ~1s whenever no connection of the pool is
    // open yet, including while an earlier attempt on THIS connection is still mid-handshake
    // (a public relay can easily take longer than 1s to open). Without this guard, each such
    // tick created and leaked a brand-new WebSocket on top of the still-pending one, since
    // nothing closed the old `socket` reference before overwriting it.
    private var connecting = false
    var isOpen: Boolean = false
        private set

    fun connect() {
        if (connecting || isOpen) return
        connecting = true
        val request = Request.Builder().url(relayUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isOpen = true
                connecting = false
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isOpen = false
                connecting = false
                Log.w(TAG, "relay connection failed: $relayUrl", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isOpen = false
                connecting = false
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "done")
        isOpen = false
        connecting = false
    }

    /** Sends `["EVENT", <event>]`. The relay's `OK` response is handled in [handleMessage] but
     *  not surfaced back to the caller individually this pass; [RelayBridge] treats "sent on an
     *  open socket" as success and does not currently retry on a relay-side rejection. */
    fun publish(event: NostrEvent) {
        val socket = socket ?: return
        val arr = JSONArray().put("EVENT").put(event.toJson())
        socket.send(arr.toString())
    }

    fun subscribe(subId: String, filter: NostrFilter) {
        val socket = socket ?: return
        val arr = JSONArray().put("REQ").put(subId).put(filter.toJson())
        socket.send(arr.toString())
    }

    fun unsubscribe(subId: String) {
        socket?.send(JSONArray().put("CLOSE").put(subId).toString())
    }

    private fun handleMessage(text: String) {
        try {
            val arr = JSONArray(text)
            when (arr.getString(0)) {
                "EVENT" -> {
                    val subId = arr.getString(1)
                    val event = parseEvent(arr.getJSONObject(2))
                    if (event.verify()) {
                        onEvent(subId, event)
                    } else {
                        Log.w(TAG, "dropped an event with an invalid id/signature from $relayUrl")
                    }
                }
                "OK" -> Log.d(TAG, "OK ${arr.optString(1)} accepted=${arr.optBoolean(2)} ${arr.optString(3)}")
                "EOSE" -> Unit
                "CLOSED" -> Log.d(TAG, "subscription closed by relay: ${arr.optString(2)}")
                "NOTICE" -> Log.i(TAG, "relay notice from $relayUrl: ${arr.optString(1)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "malformed relay message from $relayUrl, ignored", e)
        }
    }
}

private fun NostrEvent.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("pubkey", pubkey)
    put("created_at", createdAt)
    put("kind", kind)
    put("tags", JSONArray(tags.map { JSONArray(it) }))
    put("content", content)
    put("sig", sig)
}

private fun parseEvent(obj: JSONObject): NostrEvent {
    val tagsArr = obj.getJSONArray("tags")
    val tags = (0 until tagsArr.length()).map { i ->
        val tagArr = tagsArr.getJSONArray(i)
        (0 until tagArr.length()).map { j -> tagArr.getString(j) }
    }
    return NostrEvent(
        id = obj.getString("id"),
        pubkey = obj.getString("pubkey"),
        createdAt = obj.getLong("created_at"),
        kind = obj.getInt("kind"),
        tags = tags,
        content = obj.getString("content"),
        sig = obj.getString("sig"),
    )
}

/** True if this device currently has a usable internet path (Wi-Fi, cellular, or Ethernet).
 *  Does not confirm any relay is actually reachable, only that attempting to connect is
 *  worthwhile; [RelayBridge] still has to handle individual connection failures. */
fun hasInternetConnectivity(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Manages connections to [DEFAULT_RELAYS]. Kept separate from [NostrRelayConnection] (one
 *  socket) so [RelayBridge] can publish/subscribe once and have it reach every relay, same
 *  "don't depend on one relay" property NIP-01 clients generally follow. */
class NostrRelayPool(private val onEvent: (subId: String, event: NostrEvent) -> Unit, relayUrls: List<String> = DEFAULT_RELAYS) {
    private val connections = relayUrls.map { NostrRelayConnection(it, onEvent) }

    fun connectAll() = connections.forEach { it.connect() }
    fun disconnectAll() = connections.forEach { it.disconnect() }
    fun publish(event: NostrEvent) = connections.forEach { if (it.isOpen) it.publish(event) }
    fun subscribe(subId: String, filter: NostrFilter) = connections.forEach { if (it.isOpen) it.subscribe(subId, filter) }
    fun anyOpen(): Boolean = connections.any { it.isOpen }
}

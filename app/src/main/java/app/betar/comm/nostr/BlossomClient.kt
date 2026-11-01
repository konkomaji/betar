package app.betar.comm.nostr

import android.util.Log
import java.security.MessageDigest
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BlossomClient"
private const val AUTH_KIND = 24242
private const val AUTH_EXPIRY_SECONDS = 60L

/**
 * Minimal Blossom client (BUD-01 retrieval, BUD-02 upload, BUD-11 Nostr-signed auth; spec
 * fetched and read from https://github.com/hzrd149/blossom before writing this, not
 * reconstructed from memory). Lets a Feed post's full-resolution image travel over the Nostr
 * relay bridge via a content-addressed blob store, the exact gap `AppPost.kt`'s own class doc
 * states plainly ("full resolution is meant to travel... via a content-addressed blob store...
 * once that exists"). `RelayBridge.kt` is the only caller.
 *
 * **Not a claimed NIP-92 implementation:** a real Nostr "imeta" tag has its own multi-field
 * shape this client doesn't produce or parse. Betar instead tags an uploaded blob with its own
 * simple `["image", url, sha256Hex]` triple on the Feed-post event, documented here as that, not
 * claimed as a standards-compliance this doesn't have.
 *
 * **No verified default server, stated plainly:** [DEFAULT_SERVER] is not asserted to be a
 * live, trustworthy Blossom server as this is read -- this client was written without a live
 * network check (per this session's own "write code only" instruction). Picking a wrong or dead
 * server here just makes [upload] return null, the same as any other network failure; nothing
 * silently breaks differently. A real deployment should confirm [DEFAULT_SERVER] actually runs
 * a Blossom server before relying on it, or replace it with one that's been verified.
 */
object BlossomClient {
    const val DEFAULT_SERVER = "https://blossom.primal.net"

    private val client = OkHttpClient()

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Upload [bytes] to [server], authorized by [keyPair] (BUD-11: a signed kind-[AUTH_KIND]
     *  event, base64-encoded into the `Authorization: Nostr <...>` header). Returns the blob's
     *  public URL (the server's own response, not reconstructed) on success, or null on any
     *  failure -- network, auth rejection, or a malformed response -- a caller treats a null
     *  exactly like "this image stays mesh-thumbnail-only," never a crash. */
    fun upload(keyPair: NostrKeyPair, bytes: ByteArray, server: String = DEFAULT_SERVER): String? =
        try {
            val hash = sha256Hex(bytes)
            val authEvent = NostrEvent.create(
                keyPair = keyPair,
                kind = AUTH_KIND,
                tags = listOf(
                    listOf("t", "upload"),
                    listOf("x", hash),
                    listOf("expiration", (System.currentTimeMillis() / 1000L + AUTH_EXPIRY_SECONDS).toString()),
                ),
                content = "Betar image upload",
            )
            val authHeader = "Nostr " + Base64.getEncoder().encodeToString(authEvent.toAuthJson().toByteArray(Charsets.UTF_8))
            val request = Request.Builder()
                .url("${server.trimEnd('/')}/upload")
                .put(bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .addHeader("Authorization", authHeader)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "blossom upload failed: HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                JSONObject(body).optString("url").ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "blossom upload failed", e)
            null
        }

    /** Download the blob at [url] and verify it hashes to [expectedSha256] (BUD-01's `GET` is
     *  public, no auth needed; the hash check is this client's own defense-in-depth,
     *  independent of transport security, matching content-addressing's whole point). Returns
     *  null on any network failure or a hash mismatch -- a mismatch is treated as untrusted
     *  content and never handed back, under any circumstance. */
    fun download(url: String, expectedSha256: String): ByteArray? =
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "blossom download failed: HTTP ${response.code}")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                if (sha256Hex(bytes) != expectedSha256) {
                    Log.w(TAG, "blossom download hash mismatch, discarding")
                    return null
                }
                bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "blossom download failed", e)
            null
        }

    /** The exact JSON object a Blossom auth event's `Authorization` header base64-encodes --
     *  not [NostrEvent.canonicalSerialization] (that compact array form is only for computing
     *  the id, BUD-11 wants the full named-field event object, same shape a relay's `EVENT`
     *  message carries). */
    private fun NostrEvent.toAuthJson(): String = JSONObject().apply {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", JSONArray(tags.map { JSONArray(it) }))
        put("content", content)
        put("sig", sig)
    }.toString()
}

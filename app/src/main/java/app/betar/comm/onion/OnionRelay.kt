package app.betar.comm.onion

import android.util.Log
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.FfiOnionPeel
import uniffi.mesh_core.onionBuildPacket
import uniffi.mesh_core.onionHopCount
import uniffi.mesh_core.onionPeelLayer

private const val TAG = "OnionRelay"

/**
 * Kotlin-side entry point for the optional Sphinx-style onion-routing fallback
 * (`core/src/crypto/onion.rs`, `docs/CRYPTOGRAPHY.md` §7.3). Fallback-only, matching this
 * project's own scope decision (`docs/ROADMAP.md`'s "onion-routing fallback option", Phase 2):
 * nothing in this app calls [buildOnionPacket] from the default Direct-message send path, this
 * object just makes the crypto usable from Kotlin at all.
 *
 * **What is and isn't wired up, stated plainly:** route selection, that is, *which* nearby
 * relays to route through and in what order, is deliberately not decided here, the same
 * division `core/src/crypto/onion.rs`'s own doc comment draws ("a native/app-layer concern").
 * There is also no peer-discovery or relay-capability-advertisement mechanism yet for a caller
 * to build a real route from, and no forwarding wiring into `MeshCoordinator`/`FfiMeshNode` for
 * an intermediate hop to actually relay a [FfiOnionPeel.Forward] packet onward over the mesh.
 * Building those is its own follow-up; what exists today is a correct, tested way to wrap and
 * unwrap a payload in onion layers once a caller has both a route and a transport for it.
 */
object OnionRelay {
    /** Exactly how many hops [buildOnionPacket] requires `route` to have, every time. Query
     *  this rather than hard-coding the number twice, in case the fixed hop count ever changes
     *  on the Rust side. */
    val hopCount: Int by lazy { onionHopCount().toInt() }

    /** Wrap `payload` in [hopCount] layers of Sphinx-style encryption, routed through `route` in
     *  order. `route` must have exactly [hopCount] entries, each a relay's 32-byte X25519
     *  agreement public key (see [FfiIdentity.agreementKeyBytes] for a device's own). Returns
     *  null (never throws into a caller that forgot to check the route length itself) on a
     *  malformed route or an oversized payload. */
    fun buildOnionPacket(route: List<ByteArray>, payload: ByteArray): ByteArray? =
        try {
            onionBuildPacket(route, payload)
        } catch (e: Exception) {
            Log.w(TAG, "onion packet build failed", e)
            null
        }

    /** What this device's own [FfiIdentity] reveals by peeling one layer off `packet`: either
     *  it is the final destination ([Outcome.Delivered]), or it should forward [Outcome.Forward]
     *  .packet to [Outcome.Forward].nextHop (an X25519 agreement-key address, not an IP or a
     *  peer handle; resolving that address to an actual reachable mesh peer is the caller's job,
     *  not this object's). Returns [Outcome.NotForThisDevice] on any decryption failure --
     *  the ordinary, expected result when a packet addressed to someone else is simply not
     *  decryptable under this device's key, not necessarily an attack. */
    fun peelOnionLayer(identity: FfiIdentity, packet: ByteArray): Outcome =
        try {
            when (val peeled = onionPeelLayer(identity, packet)) {
                is FfiOnionPeel.Final -> Outcome.Delivered(peeled.payload)
                is FfiOnionPeel.Forward -> Outcome.Forward(peeled.nextHop, peeled.packet)
            }
        } catch (e: Exception) {
            Outcome.NotForThisDevice
        }

    sealed class Outcome {
        data class Delivered(val payload: ByteArray) : Outcome()
        data class Forward(val nextHop: ByteArray, val packet: ByteArray) : Outcome()
        object NotForThisDevice : Outcome()
    }
}

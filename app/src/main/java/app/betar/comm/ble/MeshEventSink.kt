package app.betar.comm.ble

/**
 * Bridges [BleTransportDriver]'s radio events into whatever drives the mesh protocol
 * (`FfiMeshNode` in practice). A plain interface rather than a direct `FfiMeshNode` reference
 * because the driver has to exist *before* `FfiMeshNode.open(...)` can be constructed (the node
 * constructor takes the transport) -- see plan doc §"Key design decision". `MeshCoordinator`
 * sets [BleTransportDriver.eventSink] once both objects exist.
 */
interface MeshEventSink {
    fun onPeerConnected(peerHandle: ULong)
    fun onFrame(peerHandle: ULong, bytes: ByteArray)
    fun onPeerLost(peerHandle: ULong)
}

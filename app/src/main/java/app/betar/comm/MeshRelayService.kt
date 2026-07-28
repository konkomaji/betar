package app.betar.comm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "MeshRelayService"
private const val CHANNEL_ID = "mesh_relay"
private const val NOTIFICATION_ID = 1

/**
 * Foreground service keeping the mesh (BLE advertise/scan/GATT + the `RelayEngine` it drives)
 * running while the app is backgrounded -- `docs/ARCHITECTURE.md` §4, `docs/TRANSPORT.md` §6's
 * "Android carries the background backbone." Without this, `BleTransportDriver.start()` (called
 * from `MainActivity`'s UI) stops as soon as Android freezes the app's process, per the
 * platform's standard background-execution limits -- a foreground service with a persistent
 * notification is the one exemption mechanism Android actually offers for this.
 *
 * Deliberately thin: owns no mesh logic itself, only the process-lifetime/notification plumbing
 * Android requires of a foreground service. All real mesh behavior stays in [MeshCoordinator]
 * (owned by [MeshApplication], shared with this service and [MainActivity]) — starting this
 * service is now how [MainActivity] starts the mesh, rather than calling
 * `coordinator.start()` directly, so the mesh's lifetime is tied to the service's, not the
 * activity's.
 */
class MeshRelayService : Service() {
    private val coordinator by lazy { (application as MeshApplication).coordinator }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, buildNotification(), serviceType)

        if (!coordinator.isRunning()) {
            try {
                coordinator.start()
                Log.i(TAG, "mesh started in foreground service")
            } catch (e: Exception) {
                Log.e(TAG, "failed to start mesh -- stopping service", e)
                stopSelf()
            }
        }
        // STICKY: if the system kills this process under memory pressure, restart the service
        // (with a null intent) so background relay resumes rather than silently staying off.
        return START_STICKY
    }

    override fun onDestroy() {
        coordinator.stop()
        Log.i(TAG, "mesh stopped, service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mesh_relay_channel_name),
            // LOW: a persistent status indicator, not an alert -- no sound, no heads-up popup.
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mesh_relay_notification_title))
            .setContentText(getString(R.string.mesh_relay_notification_text))
            // Placeholder platform icon -- no custom notification icon asset exists yet.
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        fun startIntent(context: Context): Intent = Intent(context, MeshRelayService::class.java)
    }
}

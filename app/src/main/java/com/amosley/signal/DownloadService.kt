package com.amosley.signal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.amosley.signal.data.DlState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the process alive while downloads from the PC are running. */
class DownloadService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Preparing downloads…", 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val app = application as SignalApp
        if (job == null) {
            job = app.scope.launch {
                app.repo.downloads.states.collectLatest { states ->
                    val running = states.values.filterIsInstance<DlState.Running>()
                    val queued = states.values.count { it is DlState.Queued }
                    val pct = running.firstOrNull()?.progress?.times(100)?.toInt() ?: 0
                    val text = "${running.size + queued} left · ${app.repo.pcName}"
                    nm.notify(NOTIFICATION_ID, build(text, pct))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, pct: Int): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 42
    }
}

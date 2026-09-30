package com.amosley.signal

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.amosley.signal.ui.AppState
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.SignalRoot
import com.amosley.signal.ui.theme.SignalTheme

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels()
    private val app get() = application as SignalApp

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.repo.rescanPhone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SignalTheme { SignalRoot(state) }
        }
        requestPermissionsIfNeeded()
        app.repo.rescanPhone()
        app.repo.startAutoRefresh()
        app.hub.connectSession()
    }

    /**
     * Volume buttons while music plays on a Sonos speaker: change the speaker's volume (not the phone's).
     * Handled here so it always works while Signal is open; in the background the MediaSession does it.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val hub = app.hub
        val up = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        val down = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        val r = hub.remoteOutput
        if ((up || down) && r != null && r.supportsVolume) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                hub.setRemoteVolume(hub.remoteVolume.value + if (up) com.amosley.signal.playback.VOLUME_STEP else -com.amosley.signal.playback.VOLUME_STEP)
                app.toast("${r.name} volume · ${hub.remoteVolume.value}")
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        app.repo.refresh()
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Leaving the app while a video plays → picture-in-picture.
        if (state.screen is Screen.Video && Build.VERSION.SDK_INT >= 26) {
            runCatching {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        state.pip = isInPictureInPictureMode
    }
}

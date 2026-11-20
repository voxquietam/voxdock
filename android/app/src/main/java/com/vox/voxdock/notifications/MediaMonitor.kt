package com.vox.voxdock.notifications

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val appLabel: String = "",
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false
)

object MediaMonitor {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var activeController: MediaController? = null

    fun refresh(context: Context) {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val component = ComponentName(context, NotificationCaptureService::class.java)
        val controllers = runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList())

        // Prefer Spotify or currently playing controller
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING && it.packageName.contains("spotify", true)
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.packageName.contains("spotify", true)
        } ?: controllers.firstOrNull()

        activeController = chosen
        if (chosen == null) {
            _state.value = NowPlayingState()
            return
        }

        val meta = chosen.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val playing = chosen.playbackState?.state == PlaybackState.STATE_PLAYING
        val app = when {
            chosen.packageName.contains("spotify", true) -> "SPOTIFY"
            chosen.packageName.contains("youtube", true) -> "YT MUSIC"
            else -> chosen.packageName.substringAfterLast('.').uppercase()
        }

        _state.value = NowPlayingState(
            title = title.ifBlank { "Nothing playing" },
            artist = artist,
            appLabel = app,
            isPlaying = playing,
            hasSession = true
        )
    }

    fun togglePlayPause() {
        val c = activeController ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        runCatching {
            if (playing) c.transportControls.pause() else c.transportControls.play()
        }
        _state.value = _state.value.copy(isPlaying = !playing)
    }

    fun next() {
        runCatching { activeController?.transportControls?.skipToNext() }
    }

    fun previous() {
        runCatching { activeController?.transportControls?.skipToPrevious() }
    }
}

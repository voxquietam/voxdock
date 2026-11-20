package com.vox.voxdock.notifications

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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

    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushFromController(activeController)
        }

        override fun onSessionDestroyed() {
            activeController = null
            _state.value = NowPlayingState()
        }
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext
        val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        sessionManager = msm
        val component = ComponentName(appCtx, NotificationCaptureService::class.java)

        if (sessionsListener == null) {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                bindBestController(controllers.orEmpty())
            }
            sessionsListener = listener
            runCatching {
                msm.addOnActiveSessionsChangedListener(listener, component, mainHandler)
            }
        }
        refresh(appCtx)
    }

    fun stop() {
        sessionsListener?.let { listener ->
            runCatching { sessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
        detachController()
    }

    fun refresh(context: Context) {
        val msm = sessionManager
            ?: (context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
            ?: return
        val component = ComponentName(context, NotificationCaptureService::class.java)
        val controllers = runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList())
        bindBestController(controllers)
    }

    private fun detachController() {
        activeController?.let {
            runCatching { it.unregisterCallback(controllerCallback) }
        }
        activeController = null
    }

    private fun bindBestController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING && it.packageName.contains("spotify", true)
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.packageName.contains("spotify", true)
        } ?: controllers.firstOrNull()

        if (chosen?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = chosen
            if (chosen != null) {
                runCatching { chosen.registerCallback(controllerCallback, mainHandler) }
            }
        }
        pushFromController(chosen)
    }

    private fun pushFromController(chosen: MediaController?) {
        if (chosen == null) {
            _state.value = NowPlayingState()
            return
        }
        val meta = chosen.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val pbState = chosen.playbackState?.state
        val playing = pbState == PlaybackState.STATE_PLAYING || pbState == PlaybackState.STATE_BUFFERING
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
        val pbState = c.playbackState?.state
        val playing = pbState == PlaybackState.STATE_PLAYING || pbState == PlaybackState.STATE_BUFFERING
        runCatching {
            if (playing) c.transportControls.pause() else c.transportControls.play()
        }
        // Real state update arrives immediately via controllerCallback.onPlaybackStateChanged
    }

    fun next() {
        runCatching { activeController?.transportControls?.skipToNext() }
    }

    fun previous() {
        runCatching { activeController?.transportControls?.skipToPrevious() }
    }
}

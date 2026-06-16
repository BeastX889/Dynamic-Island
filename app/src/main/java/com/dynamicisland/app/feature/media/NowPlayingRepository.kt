package com.dynamicisland.app.feature.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState
import com.dynamicisland.app.island.MusicAction
import com.dynamicisland.app.service.IslandNotificationListener

/**
 * Bridges the platform [MediaSessionManager] to [IslandController].
 *
 * It listens for the set of active media sessions (the same data the system uses for its own media
 * controls), tracks the highest-priority one, and publishes an [IslandState.Music] reflecting its
 * metadata and playback state. Transport actions from the island are forwarded back to that
 * session's [MediaController.TransportControls].
 *
 * Access to other apps' sessions is granted by being an enabled notification listener — we pass the
 * [IslandNotificationListener] component name, which avoids the restricted MEDIA_CONTENT_CONTROL
 * permission. If notification access has not been granted yet, [start] degrades gracefully (the
 * island simply shows no real media).
 */
class NowPlayingRepository(private val context: Context) {

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, IslandNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var activeController: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindTopController(controllers)
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = clear()
    }

    /** Advances the progress bar smoothly while playing (PlaybackState.position is a snapshot). */
    private val ticker = object : Runnable {
        override fun run() {
            publish()
        }
    }

    fun start() {
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            bindTopController(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            // Notification listener not enabled — leave the media slot empty.
        }
    }

    fun stop() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        handler.removeCallbacks(ticker)
        detach()
        IslandController.submit(IslandController.Source.MEDIA, null)
    }

    fun onAction(action: MusicAction) {
        val controls = activeController?.transportControls ?: return
        when (action) {
            MusicAction.PLAY_PAUSE ->
                if (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
            MusicAction.NEXT -> controls.skipToNext()
            MusicAction.PREVIOUS -> controls.skipToPrevious()
        }
    }

    private fun bindTopController(controllers: List<MediaController>?) {
        val top = controllers?.firstOrNull()
        if (top?.sessionToken == activeController?.sessionToken) {
            publish()
            return
        }
        detach()
        activeController = top
        top?.registerCallback(controllerCallback, handler)
        publish()
    }

    private fun detach() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun clear() {
        handler.removeCallbacks(ticker)
        detach()
        IslandController.submit(IslandController.Source.MEDIA, null)
    }

    private fun publish() {
        handler.removeCallbacks(ticker)

        val controller = activeController
        val metadata = controller?.metadata
        val playback = controller?.playbackState
        if (controller == null || metadata == null || playback == null ||
            playback.state == PlaybackState.STATE_NONE ||
            playback.state == PlaybackState.STATE_STOPPED
        ) {
            IslandController.submit(IslandController.Source.MEDIA, null)
            return
        }

        val isPlaying = playback.state == PlaybackState.STATE_PLAYING
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val position = if (isPlaying) {
            val delta = SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
            playback.position + (delta * playback.playbackSpeed).toLong()
        } else {
            playback.position
        }.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)

        // Preserve the expanded flag if the island is already showing this player.
        val expanded = (IslandController.state.value as? IslandState.Music)?.expanded ?: false

        IslandController.submit(
            IslandController.Source.MEDIA,
            IslandState.Music(
                title = title,
                artist = artist,
                artwork = artwork,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration,
                expanded = expanded,
            ),
        )

        if (isPlaying) handler.postDelayed(ticker, 1000L)
    }
}

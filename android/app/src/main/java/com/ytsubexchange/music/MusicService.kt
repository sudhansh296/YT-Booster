package com.ytsubexchange.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaNotification
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        var onNextRequested: (() -> Unit)? = null
        var onPreviousRequested: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        try {
            android.util.Log.d("MusicService", "onCreate() called")
            createNotificationChannel()

            // Show a dummy notification immediately to keep service alive
            val notification = NotificationCompat.Builder(this, "ytsubexchange_music_playback")
                .setContentTitle("YT Music")
                .setContentText("Initializing...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(1, notification)
            android.util.Log.d("MusicService", "Foreground notification shown")

            val player = ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .build()

            android.util.Log.d("MusicService", "ExoPlayer created successfully")
            player.repeatMode = Player.REPEAT_MODE_OFF

            // Create custom command buttons
            val previousButton = CommandButton.Builder()
                .setDisplayName("Previous")
                .setSessionCommand(SessionCommand("PREVIOUS", android.os.Bundle.EMPTY))
                .setIconResId(android.R.drawable.ic_media_previous)
                .build()

            val nextButton = CommandButton.Builder()
                .setDisplayName("Next")
                .setSessionCommand(SessionCommand("NEXT", android.os.Bundle.EMPTY))
                .setIconResId(android.R.drawable.ic_media_next)
                .build()

            android.util.Log.d("MusicService", "Creating MediaSession...")
            mediaSession = MediaSession.Builder(this, player)
                .setCallback(object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo
                    ): MediaSession.ConnectionResult {
                        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand("NEXT", android.os.Bundle.EMPTY))
                            .add(SessionCommand("PREVIOUS", android.os.Bundle.EMPTY))
                            .build()
                        
                        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .add(Player.COMMAND_SEEK_TO_NEXT)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .build()
                        
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(sessionCommands)
                            .setAvailablePlayerCommands(playerCommands)
                            .build()
                    }

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: SessionCommand,
                        args: android.os.Bundle
                    ): ListenableFuture<SessionResult> {
                        when (customCommand.customAction) {
                            "NEXT" -> onNextRequested?.invoke()
                            "PREVIOUS" -> onPreviousRequested?.invoke()
                        }
                        return Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        )
                    }
                })
                .setCustomLayout(ImmutableList.of(previousButton, nextButton))
                .setSessionActivity(createPendingIntent())
                .build()
            
            android.util.Log.d("MusicService", "MediaSession created successfully")
            
            // Set notification provider with custom layout
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this)
                    .setChannelId("ytsubexchange_music_playback")
                    .build()
            )
            android.util.Log.d("MusicService", "MusicService initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "FATAL: Failed to initialize MusicService", e)
            e.printStackTrace()
            throw e // Re-throw to see the crash in logcat
        }
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY = service restart hogi agar system kill kare
        return android.app.Service.START_STICKY
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // App swipe se close hone pe bhi music chalti rahe
        val player = mediaSession?.player
        if (player != null && player.playWhenReady) {
            // Music chal rahi hai - service ko alive rakho
            return
        }
        // Music nahi chal rahi - service band karo
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ytsubexchange_music_playback",
                "YT Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

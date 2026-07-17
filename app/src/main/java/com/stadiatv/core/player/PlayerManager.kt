package com.stadiatv.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.stadiatv.core.model.PlaybackRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var activePlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun play(request: PlaybackRequest): Player {
        activePlayer?.release()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.headers)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { activePlayer = it }
        val itemBuilder = MediaItem.Builder()
            .setUri(request.uri)
            .setMimeType(request.mimeType)
            .setMediaId(request.mediaId)
        if (request.isLive) {
            itemBuilder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        }
        val item = itemBuilder.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        return player
    }

    fun release() {
        activePlayer?.release()
        activePlayer = null
    }
}

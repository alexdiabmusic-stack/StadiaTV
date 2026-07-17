package com.stadiatv.core.player

import android.net.Uri
import com.stadiatv.core.database.MediaItemDao
import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.model.PlaybackRequest
import com.stadiatv.core.model.SourceType
import com.stadiatv.core.security.CredentialStore
import javax.inject.Inject

interface PlaybackResolver {
    suspend fun resolve(mediaItemId: String): PlaybackRequest
}

class PlaybackResolverCoordinator @Inject constructor(
    private val sourceDao: SourceDao,
    private val mediaItemDao: MediaItemDao,
    private val credentialStore: CredentialStore,
) : PlaybackResolver {
    override suspend fun resolve(mediaItemId: String): PlaybackRequest {
        val item = mediaItemDao.getById(mediaItemId) ?: error("Media item is unavailable")
        val source = sourceDao.getSource(item.sourceId) ?: error("Source is unavailable")
        val uri = when (source.type) {
            SourceType.M3U -> item.directStreamUrl ?: error("M3U stream URL is unavailable")
            SourceType.XTREAM -> item.directStreamUrl ?: buildXtreamUrl(source.id, source.baseUrl, item.providerItemId, item.kind, item.containerExtension)
        }
        return PlaybackRequest(
            mediaId = item.id,
            uri = Uri.parse(uri),
            mimeType = mimeType(uri, item.containerExtension),
            headers = emptyMap(),
            isLive = item.kind == MediaKind.LIVE || item.kind == MediaKind.RADIO,
            drm = null,
            displayTitle = item.name,
        )
    }

    private suspend fun buildXtreamUrl(sourceId: String, baseUrl: String?, providerItemId: String?, kind: MediaKind, extension: String?): String {
        val host = baseUrl?.trimEnd('/') ?: error("Xtream host is unavailable")
        val username = credentialStore.getSecret(sourceId, "username") ?: error("Xtream username is unavailable")
        val password = credentialStore.getSecret(sourceId, "password") ?: error("Xtream password is unavailable")
        val streamId = providerItemId ?: error("Xtream stream ID is unavailable")
        val ext = extension?.ifBlank { null } ?: if (kind == MediaKind.LIVE) "ts" else "mp4"
        val path = when (kind) {
            MediaKind.LIVE, MediaKind.RADIO -> "live"
            MediaKind.MOVIE -> "movie"
            MediaKind.EPISODE -> "series"
            MediaKind.SERIES -> error("Series containers are not directly playable")
        }
        return "$host/$path/${Uri.encode(username)}/${Uri.encode(password)}/${Uri.encode(streamId)}.$ext"
    }

    private fun mimeType(url: String, extension: String?): String? {
        val lower = url.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".m3u8") || extension == "m3u8" -> "application/x-mpegURL"
            lower.endsWith(".mpd") || extension == "mpd" -> "application/dash+xml"
            lower.endsWith(".ts") || extension == "ts" -> "video/mp2t"
            lower.endsWith(".mp4") || extension == "mp4" -> "video/mp4"
            lower.endsWith(".mkv") || extension == "mkv" -> "video/x-matroska"
            else -> null
        }
    }
}

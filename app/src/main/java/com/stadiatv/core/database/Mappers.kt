package com.stadiatv.core.database

import com.stadiatv.core.model.Category
import com.stadiatv.core.model.MediaItem
import com.stadiatv.core.model.PlaylistSource

fun PlaylistSourceEntity.toDomain() = PlaylistSource(
    id = id,
    displayName = displayName,
    type = type,
    baseUrl = baseUrl,
    playlistUrl = playlistUrl,
    epgUrl = epgUrl,
    enabled = enabled,
    status = status,
    lastAttemptAt = lastAttemptAt,
    lastSuccessfulSyncAt = lastSuccessfulSyncAt,
    lastErrorCode = lastErrorCode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CategoryEntity.toDomain() = Category(id, sourceId, providerCategoryId, name, kind, sortOrder)

fun MediaItemEntity.toDomain() = MediaItem(
    id = id,
    sourceId = sourceId,
    providerItemId = providerItemId,
    categoryId = categoryId,
    kind = kind,
    name = name,
    normalizedName = normalizedName,
    logoUrl = logoUrl,
    posterUrl = posterUrl,
    epgId = epgId,
    containerExtension = containerExtension,
    directStreamUrl = directStreamUrl,
    contentFingerprint = contentFingerprint,
    lastSeenSyncId = lastSeenSyncId,
    unavailableSince = unavailableSince,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

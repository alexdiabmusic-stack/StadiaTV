package com.stadiatv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlaylistSourceEntity::class,
        CategoryEntity::class,
        MediaItemEntity::class,
        ProgrammeEntity::class,
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        EpgChannelOverrideEntity::class,
        SyncRunEntity::class,
        SourceMetadataEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StadiaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun syncDao(): SyncDao
    abstract fun metadataDao(): MetadataDao
}

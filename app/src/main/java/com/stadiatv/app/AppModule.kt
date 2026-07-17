package com.stadiatv.app

import android.content.Context
import androidx.room.Room
import com.stadiatv.core.database.StadiaDatabase
import com.stadiatv.core.parser.M3uParser
import com.stadiatv.core.parser.XmltvParser
import com.stadiatv.core.player.PlaybackResolver
import com.stadiatv.core.player.PlaybackResolverCoordinator
import com.stadiatv.core.security.CredentialStore
import com.stadiatv.core.security.KeystoreCredentialStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StadiaDatabase =
        Room.databaseBuilder(context, StadiaDatabase::class.java, "stadiatv.db")
            .build()

    @Provides fun provideSourceDao(db: StadiaDatabase) = db.sourceDao()
    @Provides fun provideCategoryDao(db: StadiaDatabase) = db.categoryDao()
    @Provides fun provideMediaItemDao(db: StadiaDatabase) = db.mediaItemDao()
    @Provides fun provideProgrammeDao(db: StadiaDatabase) = db.programmeDao()
    @Provides fun provideFavoriteDao(db: StadiaDatabase) = db.favoriteDao()
    @Provides fun providePlaybackHistoryDao(db: StadiaDatabase) = db.playbackHistoryDao()
    @Provides fun provideSyncDao(db: StadiaDatabase) = db.syncDao()
    @Provides fun provideMetadataDao(db: StadiaDatabase) = db.metadataDao()

    @Provides fun provideM3uParser(): M3uParser = M3uParser()
    @Provides fun provideXmltvParser(): XmltvParser = XmltvParser()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds abstract fun bindCredentialStore(impl: KeystoreCredentialStore): CredentialStore
    @Binds abstract fun bindPlaybackResolver(impl: PlaybackResolverCoordinator): PlaybackResolver
}

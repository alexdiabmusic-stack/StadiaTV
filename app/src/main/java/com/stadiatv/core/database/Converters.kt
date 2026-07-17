package com.stadiatv.core.database

import androidx.room.TypeConverter
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.model.SourceStatus
import com.stadiatv.core.model.SourceType
import java.time.Instant

class Converters {
    @TypeConverter fun instantToString(value: Instant?): String? = value?.toString()
    @TypeConverter fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun sourceTypeToString(value: SourceType): String = value.name
    @TypeConverter fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)
    @TypeConverter fun mediaKindToString(value: MediaKind): String = value.name
    @TypeConverter fun stringToMediaKind(value: String): MediaKind = MediaKind.valueOf(value)
    @TypeConverter fun sourceStatusToString(value: SourceStatus): String = value.name
    @TypeConverter fun stringToSourceStatus(value: String): SourceStatus = SourceStatus.valueOf(value)
}

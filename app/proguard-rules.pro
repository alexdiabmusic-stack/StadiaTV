-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @androidx.room.* <fields>;
}

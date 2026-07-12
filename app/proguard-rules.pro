# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep JSONObject (used in export/import)
-keep class org.json.** { *; }

# Keep data classes used in serialization
-keep class com.fengnian.folderdrawer.data.** { *; }

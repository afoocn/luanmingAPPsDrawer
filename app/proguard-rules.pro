# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# 导出/导入为手写 JSONObject 解析（无反射），Room 实体已由上方 @Entity 规则覆盖，
# 故放开 com.fengnian.folderdrawer.data.** 让 R8 自由优化方法与未用成员。
# （org.json 属 Android 框架层、不打包进 APK，原 keep 规则无效已删除）

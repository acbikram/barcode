# ── Barcode To CSV — ProGuard / R8 rules ──────────────────────────────────────

# MLKit + CameraX use reflection internally
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }

# OpenCSV uses reflection for bean mapping
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Hilt generated components (Hilt has its own plugin; these are extra safety nets)
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Room: keep all entity, DAO, and database classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *; }

# JSON / Reflection (JSONObject used for WiFi protocol)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# DataStore
-keep class androidx.datastore.** { *; }

# WorkManager + Hilt-Work
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Suppress warnings from optional dependencies
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

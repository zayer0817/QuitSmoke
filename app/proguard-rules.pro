# ===========================
# QuitSmoke ProGuard Rules
# ===========================

# ---- 通用 ----
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Room 数据库 ----
-keep class com.quitsmoke.app.data.** { *; }
-dontwarn androidx.room.**

# ---- Kotlin 协程 ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- AndroidX / Lifecycle ----
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ---- AppWidget ----
-keep class com.quitsmoke.app.widget.** { *; }

# ---- Material Design ----
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ---- ViewBinding ----
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

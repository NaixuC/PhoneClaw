# Operit NG ProGuard Rules
-keep class com.phoneclaw.app.** { *; }
-keep class com.phoneclaw.app.bridge.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.linkbridge.common.model.** { *; }

# Kotlin
-keepclassmembers class **$WhenMappings {
    <fields>;
}

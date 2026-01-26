# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Room entities
-keep class com.tradeflow.data.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }

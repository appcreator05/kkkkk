# Proguard rules for HDSKay WebView & AdMob & Firebase
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.firebase.database.** { *; }

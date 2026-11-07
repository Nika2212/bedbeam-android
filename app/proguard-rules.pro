# Keep JavaScript bridge methods (added in later steps).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

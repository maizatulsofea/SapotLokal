# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Firebase — keep model class field names so JSON serialisation works
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.sapotlokal.data.model.** { *; }

# ML Kit — prevent stripping of barcode scanner classes
-keep class com.google.mlkit.** { *; }

# ZXing core — prevent stripping of QR generation classes
-keep class com.google.zxing.** { *; }

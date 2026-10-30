# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep annotations and signatures for Gson serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod,InnerClasses

# Keep serialized fields and models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.shesecure.models.** { *; }
-keep class com.example.shesecure.data.remote.** { *; }
-keep class com.example.shesecure.data.local.** { *; }
-keep class com.example.shesecure.auth.** { *; }

# OkHttp, Okio, and Retrofit rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# Material Components and AndroidX
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep line numbers for production crash debugging
-keepattributes SourceFile,LineNumberTable



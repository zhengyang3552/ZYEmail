# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Room entities
-keep class com.zy.email.data.model.** { *; }

# Keep Mail classes
-keep class com.sun.mail.** { *; }
-keep class jakarta.mail.** { *; }

# Keep MSAL classes
-keep class com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**

# Gson
-keepattributes Signature
-keepattributes Annotation
-keep class com.google.gson.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin
-keep class kotlin.** { *; }

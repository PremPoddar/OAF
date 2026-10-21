# Libraries written for the desktop JVM reference classes Android doesn't have; the code paths
# OAF uses don't touch them.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn org.slf4j.**
-dontwarn com.gemalto.jp2.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn java.lang.management.**
-dontwarn javax.script.**
-dontwarn org.apache.commons.math3.**
-dontwarn org.apache.batik.**
# POI and PDFBox build some objects reflectively.
-keep class org.apache.poi.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keepclassmembers class com.prempoddar.oaf.** { *** Companion; }
-keepclasseswithmembers class com.prempoddar.oaf.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class org.apache.logging.log4j.** { *; }
# Web pages call these bridge methods by name.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
# Network drives. JSch and BouncyCastle look up their algorithms by class name.
-keep class com.jcraft.jsch.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.**
-dontwarn javax.el.**
-dontwarn net.engio.**
-dontwarn org.apache.log4j.**
# Joining split apps into one APK, and signing it.
-keep class com.reandroid.** { *; }
-keep class com.android.apksig.** { *; }
-dontwarn com.reandroid.**
-dontwarn com.android.apksig.**

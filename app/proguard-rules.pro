# kotlinx.serialization：保留 @Serializable 类的合成序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class xyz.yihaozhan.app.** { *** Companion; }
-keepclasseswithmembers class xyz.yihaozhan.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class xyz.yihaozhan.app.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

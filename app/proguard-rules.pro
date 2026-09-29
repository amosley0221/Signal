# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.amosley.signal.**$$serializer { *; }
-keepclassmembers class com.amosley.signal.** { *** Companion; }
-keepclasseswithmembers class com.amosley.signal.** { kotlinx.serialization.KSerializer serializer(...); }
# Cast options provider is referenced from the manifest by name
-keep class com.amosley.signal.cast.CastOptionsProvider { *; }
-dontwarn org.slf4j.**

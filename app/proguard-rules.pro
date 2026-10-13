# kotlinx.serialization: keep generated serializers for our DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.eaglelan.app.**$$serializer { *; }
-keepclassmembers class com.eaglelan.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.eaglelan.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

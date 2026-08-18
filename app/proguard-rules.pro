# Add project specific ProGuard rules here.
# Keep Supabase serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.pulse.statusapp.**$$serializer { *; }
-keepclassmembers class com.pulse.statusapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.pulse.statusapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep detection models / kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class ltechnologies.onionphone.imsnitch.** {
    *** Companion;
}
-keepclasseswithmembers class ltechnologies.onionphone.imsnitch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

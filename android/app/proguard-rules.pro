-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.deckvault.app.model.**$$serializer { *; }
-keepclassmembers class com.deckvault.app.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.deckvault.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**

# ---- kotlinx.serialization ----
# Saves are serialized reflectively through generated serializers, so the model
# classes and their generated serializers must survive shrinking and keep their
# original names. The domain model is small, so keeping it wholesale is cheap
# and removes any risk of a save failing to load in a release build.
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**

-keep class com.footymanager.simulator.domain.model.** { *; }
-keep class com.footymanager.simulator.domain.model.**$$serializer { *; }
-keepclassmembers class com.footymanager.simulator.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.footymanager.simulator.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Generic rules as a safety net for any serializable class outside the model.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }

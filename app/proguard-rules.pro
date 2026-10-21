# Release shrinking (R8) rules.

# The Anthropic SDK serializes requests and responses with Jackson (incl. the Kotlin module), which finds
# classes and members by reflection. Keep both whole rather than risk silently broken AI requests.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations

# JVM-only classes referenced by optional code paths in Jackson and the Anthropic SDK (java.beans, schema generation
# from annotated classes). This app builds its JSON schema by hand, so these paths never run on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

# ML Kit (the barcode scanner) instantiates the component registrars listed in the manifest by reflection. R8 keeps
# their names but drops the unused no-arg constructors, so ML Kit registers nothing and the scanner crashes on start.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keepclassmembers class app.pulse.android.utils.ActionReceiver$* {
    <fields>;
    <methods>;
}

-if @app.pulse.android.utils.ActionReceiver class **
-keepclassmembers class <1> {
    <fields>;
    <methods>;
}

#noinspection ShrinkerUnresolvedReference
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    #noinspection ShrinkerUnresolvedReference
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    #noinspection ShrinkerUnresolvedReference
    kotlinx.serialization.KSerializer serializer(...);
}

-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn ch.qos.logback.**
-dontwarn org.slf4j.**

# ML Kit discovers ComponentRegistrar classes by reflection from manifest
# metadata, so R8 full mode strips their no-arg constructors as unused
# (firebase-components 16.0.0 keeps only the class, not the ctor). Then
# discovery silently skips the registrar, the component registry ends up
# empty, and LanguageIdentification.getClient() returns a null Factory
# which NPEs. This is Google's fix from firebase-components 19.0, applied
# to both the language-id and translate registrars.
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
-keep,allowshrinking interface com.google.firebase.components.ComponentRegistrar

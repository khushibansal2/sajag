# Ed25519 and BLAKE2s are looked up reflectively in places; R8 must not strip them.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# bcprov's LDAP certificate store refers to JNDI (javax.naming), which Android
# does not ship. Nothing in the app uses it, but the -keep above keeps it, so R8
# must be told the missing classes are expected.
-dontwarn javax.naming.**

# Filament and SceneView reach Java from native code by name, and ARCore is
# loaded reflectively by Play Services for AR. Keep them whole.
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**
-dontwarn io.github.sceneview.**
-dontwarn com.github.kittinunf.**
-dontwarn kotlinx.android.parcel.**

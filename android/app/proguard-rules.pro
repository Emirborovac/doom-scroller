# The accessibility service is instantiated by the system from the manifest,
# so its name must survive shrinking.
-keep class com.rippleit.coast.ScrollService { *; }
-keep class com.rippleit.coast.MainActivity { *; }

# Flutter embedding.
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**

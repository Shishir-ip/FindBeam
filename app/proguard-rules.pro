# FindBeam R8 rules.
# Compose, Kotlin coroutines and AndroidX ship their own consumer rules;
# only a few defensive keeps are needed here.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep crash-report-friendly line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# org.json is provided by the platform
-dontwarn org.json.**

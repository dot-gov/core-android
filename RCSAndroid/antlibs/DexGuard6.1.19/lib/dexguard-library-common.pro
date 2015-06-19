# Common DexGuard configuration for debug versions and release versions.
# Copyright (c) 2012-2015 GuardSquare NV
#
# Note that the DexGuard plugin jars generally contain their own copies
# of this file.

# Keep some attributes that the compiler needs.
-keepattributes Exceptions,Deprecated,EnclosingMethod

# Keep all public API.
-keep public class * {
    public protected *;
}

-include dexguard-common.pro

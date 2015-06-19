# DexGuard configuration for release versions.
# Copyright (c) 2012-2015 GuardSquare NV
#
# Note that the DexGuard plugin jars generally contain their own copies
# of this file.

-optimizationpasses 5

-obfuscationdictionary      dictionary.txt
-classobfuscationdictionary classdictionary.txt

-include dexguard-library-common.pro
-include dexguard-assumptions.pro

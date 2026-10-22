# CrashX consumer rules
# Library classes are reached through manifest components and Java
# serialization inside the consuming app; keep the public surface so R8
# cannot strip the error/restart flows of minified apps.
-keep public class com.developer.crashx.** { public *; }

# OpenCV uses JNI; keep its native bridges.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

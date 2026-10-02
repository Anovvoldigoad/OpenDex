# Build status — v0.4.0

Architecture: DroidUP original launcher + Android host VirtualDisplay + Shizuku.

Static checks performed in generation environment:
- original launcher payload SHA-256 verified
- manifest/resources XML parsed
- no Gradle wrapper JAR committed
- required source/AIDL files present
- CI uses JDK 17 + SDK 36 + Gradle 9.5 + AGP 9.3.2

Full Android compilation must be confirmed by GitHub Actions because this generation environment does not contain the Android SDK/Maven dependencies.

Runtime success is device/OEM dependent and must be tested on the target phone.

# Change Log for Amazon Corretto 21

The following sections describe the changes for each release of Amazon Corretto 21.

## Corretto version: 21.0.0.35.1
Release Date: August 24, 2023

**Target Platforms**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Alpine-based Linux, aarch64
+ Windows 10 or later, x86_64
+ macos 11.0 and later, x86_64
+ macos 11.0 and later, aarch64


The following issues are addressed in 21.0.0.35.1:

| Issue Name                                                        | Platform | Description                                                                        | Link                                                                   |
|-------------------------------------------------------------------|----------|------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| Import jdk-21+35                                                  | All      | Updates Corretto baseline to OpenJDK 21+35                                         | [jdk-21+35](https://github.com/openjdk/jdk21/releases/tag/jdk-21%2B35) |
| 8313765: Invalid CEN header (invalid zip64 extra data field size) | All      | Fix ZipException that may be encountered when opening select APK, ZIP or JAR files | [JDK-8313765](https://bugs.openjdk.org/browse/JDK-8313765)             |

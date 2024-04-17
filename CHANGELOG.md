# Change Log for Amazon Corretto 21

The following sections describe the changes for each release of Amazon Corretto 21.

## Corretto version: 21.0.3.9.1
Release Date: April 16, 2024

**Target Platforms <sup>1</sup>**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Alpine-based Linux, aarch64
+ Windows 10 or later, x86_64
+ macos 12.0 and later, x86_64
+ macos 12.0 and later, aarch64

**1.** This is the platform targeted by the build. See [Using Amazon Corretto](https://aws.amazon.com/corretto/faqs/#Using_Amazon_Corretto)
in the Amazon Corretto FAQ for supported platforms

The following issues are addressed in 21.0.3.9.1:

| Issue Name           | Platform | Description                                                                             | Link                                                                         |
|----------------------|----------|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Import jdk-21.0.3+9 | All      | Updates Corretto baseline to OpenJDK 21.0.3+9                                          | [jdk-21.0.3+9](https://github.com/openjdk/jdk21u/releases/tag/jdk-21.0.3+9)|
| Launcher causes lingering busy cursor | All      | Fix lingering busy cursor        | [JDK-8294699](https://bugs.openjdk.org/browse/JDK-8294699)         |
| Fallback option for POST-only OCSP requests  | All      | Add option to fallback to old OCSP behaviour         | [JDK-8328638](https://bugs.openjdk.org/browse/JDK-8328638)         |
| Shenandoah: SIGSEGV crash in unnecessary_acquire due to LoadStore split through phi | All | Fix SIGSEGV crash when using Shenandoah garbage collector | [JDK-8325372](https://bugs.openjdk.org/browse/JDK-8325372) |
| Fix AL jre alternative path | AL2023 | Fix AL jre alternative path | [#55](https://github.com/corretto/corretto-21/pull/55) |


The following CVEs are addressed in 21.0.3.9.1:


| CVE            | CVSS | Component             |
|----------------|------|-----------------------|
| CVE-2024-21011 | 3.7  | hotspot/runtime       |
| CVE-2024-21012 | 3.7  | core-libs/java.net    |
| CVE-2024-21068 | 3.7  | hotspot/compiler      |
| CVE-2024-21094 | 3.7  | hotspot/compiler      |


## Corretto version: 21.0.2.13.1
Release Date: January 16, 2024

**Target Platforms <sup>1</sup>**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Alpine-based Linux, aarch64
+ Windows 10 or later, x86_64
+ macos 12.0 and later, x86_64
+ macos 12.0 and later, aarch64

**1.** This is the platform targeted by the build. See [Using Amazon Corretto](https://aws.amazon.com/corretto/faqs/#Using_Amazon_Corretto)
in the Amazon Corretto FAQ for supported platforms

The following issues are addressed in 21.0.2.13.1:

| Issue Name           | Platform | Description                                                                             | Link                                                                         |
|----------------------|----------|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Import jdk-21.0.2+13 | All      | Updates Corretto baseline to OpenJDK 21.0.2+13                                          | [jdk-21.0.2+13](https://github.com/openjdk/jdk21u/releases/tag/jdk-21.0.2+13)|
| Data loss in AVX3 Base64 decoding | All      | Base64 appears to give different (wrong) results in some rare cases when AVX3 is enabled. | [JDK-8321599](https://bugs.openjdk.org/browse/JDK-8321599)  |
| (tz) Update Timezone Data to 2023d | All      | Update Timezone Data to 2023d        | [JDK-8322725](https://bugs.openjdk.org/browse/JDK-8322725)         |
| NPE in PKCS7.parseOldSignedData    | All      | Fixes exception PKCS7.parseOldSignedDat   | [JDK-8315042](https://bugs.openjdk.org/browse/JDK-8315042)                                     |
|Enable UseCryptoPmullForCRC32 for Neoverse V2| All      |Enable UseCryptoPmullForCRC32| [JDK-8321105](https://bugs.openjdk.org/browse/JDK-8321105)                                         |
|Enable Neoverse N1 optimizations for Neoverse V2| All      |Enable Neoverse N1 optimizations for Neoverse V2| [JDK-8321025](https://bugs.openjdk.org/browse/JDK-8321025)                      |

The following CVEs are addressed in 21.0.2.13.1:

| CVE            | CVSS | Component                      |
|----------------|------|--------------------------------|
| CVE-2024-20918 | 7.4  | hotspot/compiler               |
| CVE-2024-20952 | 7.4  | security-libs/java.security    |
| CVE-2024-20919 | 5.9  | hotspot/runtime                |
| CVE-2024-20921 | 5.9  | hotspot/compiler               |
| CVE-2024-20945 | 4.7  | security-libs/javax.xml.crypto |


## Corretto version: 21.0.1.12.1
Release Date: October 17, 2023

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


The following issues are addressed in 21.0.1.12.1:

| Issue Name                                                        | Platform | Description                                                                        | Link                                                                   |
|-------------------------------------------------------------------|----------|------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| Import jdk-21.0.1+12                | All    | Updates Corretto baseline to OpenJDK 21.0.1+12   | [jdk-21.0.1+12](https://github.com/openjdk/jdk21u/releases/tag/jdk-21.0.1+12) |
| JDK-8313765                     | All                | Validations on ZIP64 Extra Fields | [JDK-8313765](https://bugs.openjdk.org/browse/JDK-8313765) |
| JDK-8316468 | All | os::write incorrectly handles partial write | [#23](https://github.com/corretto/corretto-21/pull/23)

The following CVEs are addressed in 21.0.1.12.1:

| CVE            | CVSS | Component                   |
|----------------|------|-----------------------------|
| CVE-2023-22081 | 5.3  | security-libs/javax.net.ssl |
| CVE-2023-22025 | 3.7  | hotspot/compiler            |

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

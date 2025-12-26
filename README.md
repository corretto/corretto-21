## Corretto 21

Amazon Corretto is a no-cost, multiplatform,
production-ready distribution of the Open Java Development Kit (OpenJDK).
Corretto is used internally at Amazon for production services.
With Corretto, you can develop and run Java applications
on operating systems such as Linux, Windows, and macOS.

This repository is used to track [OpenJDK 21u](https://github.com/openjdk/jdk21u).
Please look at the branches section for more information on Feature Releases.

Documentation is available at [https://docs.aws.amazon.com/corretto](https://docs.aws.amazon.com/corretto).

### Licenses and Trademarks

Please read these files: "LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION", "TRADEMARKS.md".

### Branches

_develop_
: The default branch. The branch that consumes development and patches to upstream [openjdk/jdk21u](https://github.com/openjdk/jdk21u). Corretto builds are generated from this branch.

### Download Links
Release can be found by version on the github [release page](https://github.com/corretto/corretto-21/releases). Nightly builds can be found on our [download page](https://downloads.corretto.aws/#/downloads?build=nightly&version=21)


### OpenJDK Readme
```

# Welcome to OpenJDK 21 Updates!

The JDK 21 Updates project uses two GitHub repositories.
Updates are continuously developed in the repository [jdk21u-dev](https://github.com/openjdk/jdk21u-dev). This is the repository usually targeted by contributors.
The [jdk21u](https://github.com/openjdk/jdk21u) repository is used for rampdown of the update releases of jdk21u and only accepts critical changes that must make the next release during rampdown. (You probably do not want to target jdk21u).

For more OpenJDK 21 updates specific information such as timelines and contribution guidelines see the [project wiki page](https://wiki.openjdk.org/display/JDKUpdates/JDK+21u/).


For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.


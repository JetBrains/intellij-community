# Project Model Updater

This module contains a tool for interacting with the Kotlin compiler.   
In particular, it is related to [Kotlin Cooperative Development](../../docs/cooperative-development/environment-setup.md).

## Run Configurations

All run configurations starts with `Kotlin Coop:` prefix and support as arguments:

1. Properties from [model.properties](resources/model.properties) (the base configuration file stored in Git)
2. Properties from [local.properties](resources/local.properties) (the local configuration file excluded from Git)
3. A set of additional properties passed as command line arguments (with the same `key=value` format)

**The order of arguments is important**: the last one overrides the previous ones.

Alternatively, you can run all configurations below from a terminal via `gradle run` command with required arguments (they can be found in the corresponding run configuration).

### `Kotlin Coop: Publish Compiler JARs`

The configuration runs the Kotlin compiler artifacts publication directly to this project. This is needed to work with a bootstrap compiler in `kt-master` setup.  

#### Essential options:

- `kotlinCompilerRepoPath` – path to the local copy of the Kotlin compiler repository. It can be either a relative or an absolute path. `.` points to the root of this project
  - Default: `..` (the parent directory of this project)

### `Kotlin Coop: Update Compiler Version`

The configuration applies current settings to the project. In particular, it generates libraries depending on the provided compiler version.

#### Essential options:

- `kotlincVersion` – version of the Kotlin compiler to use in the project for kotlinc libraries. Usually some dev version
- `jpsPluginVersion` – version of the bundled Kotlin JPS plugin which can be used to build user projects. Usually the last stable version
- `convertJpsToBazel` – whether to convert the JPS project model to Bazel
  - Default: `false`

### `Kotlin Coop: Advance Compiler Version`

The configuration automatically updates the compiler version in the project and commits the changes.

#### Essential options:

- `newKotlincVersion` – version of the Kotlin compiler to use in the project for kotlinc libraries. It has to be a publicly available version
  - Default: the version is requested interactively
- `kotlinCompilerRepoPath` – path to the local copy of the Kotlin compiler repository. It is recommended to use `local.properties` file to specify it to not place the project inside the Kotlin repository
  - Default: `..` (the parent directory of this project)

### `Kotlin Coop: Switch to Bootstrap`

The configuration switches the Kotlin compiler version in the project to the bootstrap mode. It is a necessary step to work with a bootstrap compiler in `kt-master` setup locally.

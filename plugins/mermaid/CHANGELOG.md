<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IntelliJ Platform Plugin Template Changelog

## [Unreleased]

## [1.1.2]
### Changed
- Update `platformVersion` to `2021.1.3` for compatibility with Apple M1
- Change since/until build to `211-213.*` (2021.1 - 2021.3)
- Upgrade Gradle Wrapper to `7.4`
- Dependencies - upgrade `org.jetbrains.intellij` to `1.4.0`
- Dependencies (GitHub Actions) - upgrade `JetBrains/qodana-action` to `4.2.5`

## [1.1.1]
### Changed
- GitHub Actions - fixed duplicated `.zip` extension in artifact file's name of the build flow
- Upgrade Gradle Wrapper to `7.3.3`
- Dependencies - upgrade `org.jetbrains.intellij` to `1.3.1`
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.6.10`
- Dependencies (GitHub Actions) - upgrade `JetBrains/qodana-action` to `4.2.3`
- Dependencies (GitHub Actions) - upgrade `actions/cache` to `v2.1.7`

## [1.1.0]
### Added
- GitHub Actions: Collect Qodana/Tests/Plugin Verifier results as artifacts

### Changed
- Dependencies - upgrade `org.jetbrains.intellij` to `1.3.0` 
- Dependencies - upgrade `org.jetbrains.changelog` to `1.3.1`
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.6.0`
- Dependencies (GitHub Actions) - upgrade `jtalk/url-health-check-action` to `2`
- Dependencies (GitHub Actions) - upgrade `actions/checkout` to `2.3.5`
- GitHub Actions general performance refactoring
- GitHub Actions - prepare plugin archive content to be archived once
- GitHub Actions - patch changelog only if change notes are provided
- Update `pluginUntilBuild` to include `213.*` (2021.3.*)
- Upgrade Gradle Wrapper to `7.3`

### Fixed
- Fixed passing change notes from `CHANGELOG.md` to the Release Draft
- Fixed passing updated change notes from the Release Draft to `patchChangelog` Gradle task
- Fixed `QODANA_SHOW_REPORT` environment variable resolving for Gradle `6.x` 

### Removed
- Removed the `pluginVerifierIdeVersions` configuration to use default IDEs list provided by the `listProductsReleases` task for `runPluginVerifier` 
- Removed `platformDownloadSources` from Gradle configuration to use default value 
- Removed `updateSinceUntilBuild.set(true)` from Gradle configuration to use default value

## [1.0.0]
### Added
- Plugin Signing
- Qodana integration
- Functional tests
- Compatibility with Java 11
- `Run Qodana` and `Run IDE for UI Tests` run configurations
- Use Gradle `wrapper` task to handle Gradle updates
- JVM compatibility version extracted to `gradle.properties` file
- Suppress `UnusedProperty` inspection for the `kotlin.stdlib.default.dependency` in `gradle.properties`

### Changed
- GitHub Actions: Use Java 11
- GitHub Actions: Update Build and Release flows
- GitHub Actions: Use Gradle cache provided with `actions/setup-java`
- Update `pluginVerifierIdeVersions` to `2020.3.4, 2021.1.3, 2021.2.1`
- Change since/until build to `203-212.*`
- Upgrade Gradle Wrapper to `7.2`
- Gradle – Changelog plugin configuration update
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.5.30`
- Dependencies - upgrade `org.jetbrains.changelog` to `1.3.0`
- Dependencies - upgrade `org.jetbrains.intellij` to `1.1.6`
- Dependencies (GitHub Actions) - upgrade `actions/upload-artifact` to `v2.2.4`

### Fixed
- Use `DynamicBundle` instead of `AbstractBundle` in `MyBundle.kt`

### Removed
- Removed `detekt`/`ktlint` integration

## [0.10.1]
### Added
- Introduced `next` branch in the root repository to make `main` always a stable one

### Changed
- Dependencies (GitHub Actions) - upgrade `actions/cache` to `v2.1.6`
- Trigger GitHub Actions `Build` workflows only on pushes to `main` branch or pull request to avoid duplicated checks

## [0.10.0]
### Changed
- Remove reference to the `jcenter()` from Gradle configuration file
- Update `pluginVerifierIdeVersions` to `2020.2.4, 2020.3.4, 2021.1.2`
- Update `pluginUntilBuild` to include `211.*` (2021.1.*)
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.5.10`
- Dependencies - upgrade `detekt-formatting from` to `1.17.1`
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.17.1`
- Dependencies (GitHub Actions) - upgrade `actions/cache` to `v2.1.5`
- Dependencies (GitHub Actions) - upgrade `actions/checkout` to `v2.3.4`
- Dependencies (GitHub Actions) - upgrade `actions/upload-release-asset` to `v1.0.2`
- Dependencies (GitHub Actions) - upgrade `actions/create-release` to `v1.1.4`
- Upgrade Gradle Wrapper to `7.0.2`

## [0.9.0]
### Added
- `properties` shorthand function for accessing `gradle.properties` in a cleaner way
- Dependabot check for GitHub Actions used in [workflow files](.github/workflows)

### Changed
- Dependencies - upgrade `detekt-formatting from` to `1.16.0` 
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.16.0` 
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.32` 
- Dependencies (GitHub Actions) - upgrade `actions/upload-artifact` to `v2.2.2`
- Dependencies (GitHub Actions) - upgrade `actions/cache` to `v2.1.4`

### Fixed
- Fix `README.md` file resolution in the `build.gradle.kts`

## [0.8.3]
### Changed
- Dependencies - upgrade `org.jetbrains.intellij` to `0.7.2`
- Dependencies - upgrade `org.jlleitschuh.gradle.ktlint` to `10.0.0`
- Update `platformVersion` to `2020.2.4` for compatibility with macOS Big Sur
- Upgrade Gradle Wrapper to `6.8.3`

## [0.8.2]
### Changed
- Use `-bin` distribution of the Gradle Wrapper
- Upgrade Gradle Wrapper to `6.8.2`
- Update `pluginVerifierIdeVersions` in `gradle.properties` files
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.30`
- Dependencies - upgrade `org.jetbrains.changelog` to `1.1.1`
- Configure the `changelog` Gradle plugin

## [0.8.1]
### Added
- README: Dependencies management section

### Changed
- Upgrade Gradle Wrapper to `6.8`
- Dependencies - upgrade `org.jetbrains.changelog` to `1.0.0`

### Fixed
- Template Cleanup: Escape GitHub username to avoid incorrect characters in class package name
- Template Cleanup: Run `ktlintFormat` task to fix imports order
- GitHub Actions: Use the correct property in the "Upload artifact" step

## [0.8.0]
### Added
- Dependabot integration
- Show `idea.log` logs of the run IDE in the Run console
- README: FAQ section

### Changed
- `build.gradle.kts`: simpler syntax for configuring `KotlinCompile`
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.21`
- Dependencies - upgrade `detekt-formatting` to `1.15.0`
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.15.0`
- README: Clarify the Java usage in the project
- `pluginVerifierIdeVersions` - upgrade to `2020.1.4, 2020.2.3, 2020.3.1`

### Fixed
- Return `Supplier<@Nls String>` instead of `String` in `MyBundle.messagePointer`

## [0.7.1]
### Changed
- Upgrade Gradle Wrapper to `6.7.1`
- Dependencies - upgrade `org.jetbrains.intellij` to `0.6.5`
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.20`
- Update the base platform version to 2020.1
- Change since/until build to `201-203.*`

## [0.7.0]
### Added
- Predefined Run/Debug Configurations
- Project icon for development purposes

### Changed
- Dependencies - upgrade `org.jetbrains.intellij` to `0.6.3`

## [0.6.1]
### Added
- GitHub Actions - use hash based on `pluginVerifierIdeVersions` in `Setup Plugin Verifier IDEs Cache` step

### Changed
- Use [Kotlin extension function](https://plugins.jetbrains.com/docs/intellij/plugin-services.html#retrieving-a-service) to retrieve the `MyProjectService` in the `MyProjectManagerListener`
- Dependencies - upgrade `org.jetbrains.intellij` to `0.6.2`
- Update `pluginVerifierIdeVersions` in the `gradle.properties` files

## [0.6.0]
### Added
- Integration with [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) through the [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin#plugin-verifier-dsl) `runPluginVerifier` task
- Cache downloaded IDEs used by Plugin Verifier for the verification

### Changed
- Switch Gradle Wrapper to `-all` to improve the IntelliSense
- Update detekt config to be in line with IJ settings
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.14.2`
- Dependencies - upgrade `org.jetbrains.intellij` to `0.6.1`
- GitHub Actions - `gradleValidation` update to `gradle/wrapper-validation-action@v1.0.3`
- GitHub Actions - `releaseDraft` update to `actions/download-artifact@v2`

### Removed
- Remove Third-party IntelliJ Plugin Verifier GitHub Action

## [0.5.1]
### Added
- Missing properties in the `gradle.properties` template file

### Changed
- Upgrade Gradle Wrapper to `6.7`
- Dependencies - upgrade `org.jetbrains.changelog` to `0.6.2`

## [0.5.0]
### Added
- Introduced `platformPlugins` property in `gradle.properties` for configuring dependencies to bundled/external plugins

### Changed
- Disable "Release Draft" job for pull requests in the "Build" GitHub Actions Workflow
- Dependencies - upgrade `org.jetbrains.intellij` to `0.5.0`
- Dependencies - upgrade `org.jetbrains.changelog` to `0.6.1`
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.14.1`
- Dependencies - upgrade `org.jlleitschuh.gradle.ktlint` to `9.4.1`
- Remove LICENSE file during the Template Cleanup workflow

## [0.4.0]
### Added
- Fix default to opt-out of bundling Kotlin standard library in plugin distribution

### Changed
- GitHub Actions: allow releasing plugin even for the base project
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.10`
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.13.1`

### Fixed
- `pluginName` variable name collision with `intellij` closure getter in Gradle configuration #29

## [0.3.2]
### Changed
- Simplify and optimize GitHub Actions
- Gradle Wrapper upgrade to `6.6.1`
- Dependencies - upgrade `org.jetbrains.kotlin.jvm` to `1.4.0`
- Dependencies - upgrade `org.jetbrains.intellij` to `0.4.22`
- Dependencies - upgrade `org.jetbrains.changelog` to `0.5.0`
- Dependencies - upgrade `io.gitlab.arturbosch.detekt` to `1.12.0`
- Dependencies - upgrade `org.jlleitschuh.gradle.ktlint` to `9.4.0`
- Rename `master` branch to `main`

### Fixed
- GitHub Actions - cache Gradle dependencies and wrapper separately

## [0.3.1]
### Added
- Better handling of the Gradle plugin description extraction from the README file
- GitHub Actions - cache Gradle Wrapper

### Changed
- Gradle - remove kotlin("stdlib-jdk8") dependency to decrease the plugin artifact size
- Dependencies - bump ktlint to `9.3.0`
- GitHub Actions - make *Update Changelog* job dependent on the *Publish Plugin*
- GitHub Actions - run plugin verifier against `2019.3` `2020.1` `2020.2`

### Fixed
- Resolve ktlint reports
- GitHub Actions - Plugin Verifier broken for artifacts with whitespaces in name

## [0.3.0]
### Added
- Set publish channel depending on the plugin version, i.e. `1.0.0-beta` -> `beta` channel

### Changed
- Update `org.jetbrains.changelog` dependency to `v0.3.3`
- Update Gradle Wrapper to `v6.5.1`
- Run GitHub Actions Release workflow on `prereleased` event
- GitHub Actions - Release - separate changelog related job from the release

### Fixed
- Remove vendor website from `plugin.xml`
- Update Template Cleanup workflow test to avoid running it on forks

## [0.2.0]
### Added
- JetBrains Plugin badges and TODO list for the end users
- `ktlint` integration

### Changed
- `pluginUntilBuild` set to the correct format: `201.*`
- Bump detekt dependency to `1.10.0`

### Fixed
- GitHub Actions - Template Cleanup - fixed adding files to git
- Update Template plugin name on cleanup
- Set `buildUponDefaultConfig = true` in detekt configuration

## [0.1.0]
### Added
- `settings.gradle.kts` for the [performance purposes](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#always_define_a_settings_file)
- `#REMOVE-ON-CLEANUP#` token to mark content to be removed with **Template Cleanup** workflow

### Changed
- README proofreading
- GitHub Actions - Update IDE versions for the Plugin Verifier
- Update platformVersion to `2020.1.2`

## [0.0.2]
### Added
- [Gradle Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin) integration

### Changed
- Bump Detekt version
- Change pluginSinceBuild to 193

## [0.0.1]
### Added
- Initial project scaffold
- GitHub Actions to automate testing and deployment
- Kotlin support
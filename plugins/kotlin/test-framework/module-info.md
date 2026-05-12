### Module `intellij.kotlin.testFramework`

Primary test framework for Kotlin IDE plugin tests — the module most test classes depend on.

This module builds on top of `intellij.kotlin.base.testFramework` (core test infrastructure)
and `intellij.kotlin.base.plugin.testFramework` (artifact provisioning) to provide
a rich, ready-to-use set of test base classes, project descriptors, and utilities
covering the full range of Kotlin IDE testing scenarios.

**Test base classes:**
- `KotlinLightCodeInsightFixtureTestCase` — the recommended base class for most Kotlin plugin tests.
  Automatically selects a project descriptor based on in-file directives (`RUNTIME`, `WITH_STDLIB`,
  `JS`, `ENABLE_MULTIPLATFORM`, etc.), configures compiler options, manages code style, and provides
  completion and action execution helpers.
- `AbstractMultiModuleTest` — heavy-weight base for tests spanning multiple modules with full daemon analysis.
- Legacy bases (`KotlinCodeInsightTestCase`, `KotlinLightCodeInsightTestCase`) for older tests.

**Project descriptors** for various platforms and configurations:
- `KotlinJdkAndLibraryProjectDescriptor` — JDK + Kotlin stdlib
- `KotlinMultiplatformProjectDescriptor` — Kotlin Multiplatform projects
- `KotlinStdJSProjectDescriptor` — Kotlin/JS
- `KotlinLightJava9ModulesCodeInsightFixtureTestCase` — Java 9+ module system
- Multiple JDK version descriptors (JDK 6, 8, 9, 11, 17, preview)

**Test library management:**
- `ConfigLibraryUtil` — attaches/detaches Kotlin runtime, coroutines, JUnit, TestNG and other libraries to test projects.
- `MockLibraryFacility` — compiles Kotlin sources into a JAR on the fly for testing against library code,
  with K1/K2 frontend support.

**Directive-based test configuration:**
tests declare their requirements via in-file directives (e.g., `// RUNTIME_WITH_SOURCES`,
`// LANGUAGE_VERSION: 2.0`, `// WITH_COROUTINES`, `// WITH_LIBRARY: myLib`), and the framework
automatically configures the test environment accordingly.

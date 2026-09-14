# IntelliJ Standalone Analysis artifacts

The IntelliJ Standalone Analysis artifacts give clients such as the Kotlin compiler or the Kotlin Analysis API a way to use the PSI and
core Java code resolution without having to set up all necessary IntelliJ parts manually. Currently, the artifacts include the platform
layer (language-agnostic services) and the Java layer (Java PSI and semantic code analysis).

Each layer is further split into the *API* and *Implementation* parts:

- The API part exposes classes meant for direct user consumption, including fundamental interfaces such as `Project`, `VirtualFile`,
  `PsiElement` or `PsiMember` (for the Java layer).
- The implementation part contains everything else needed so the API part can run. For clients, it should be a runtime-only dependency.

| Layer                   | Module                                      | Maven coordinates                                         |
|-------------------------|---------------------------------------------|-----------------------------------------------------------|
| Platform API            | `intellij.standaloneAnalysis.platform`      | `com.jetbrains.intellij.standaloneAnalysis:platform`      |
| Platform implementation | `intellij.standaloneAnalysis.platform.impl` | `com.jetbrains.intellij.standaloneAnalysis:platform-impl` |
| Java API                | `intellij.standaloneAnalysis.java`          | `com.jetbrains.intellij.standaloneAnalysis:java`          |
| Java implementation     | `intellij.standaloneAnalysis.java.impl`     | `com.jetbrains.intellij.standaloneAnalysis:java-impl`     |

## Module structure

Each layer is a POM-only artifact (`packaging=pom`) of an aggregator module. The POM lists the direct dependencies of  the module:
the lower layers, the IntelliJ modules, and the libraries. Maven resolves the rest through the regular IntelliJ module artifacts.

Two checked-in files per layer pin its content, so the artifacts do not change uncontrollably:

- `expected-pom.xml` is the generated POM.
- `expected-classpath.txt` lists the modules and the libraries that a consumer gets through the layer and not through a lower layer.
  A new dependency of a core module changes this file. Library versions are not in it, so a library update does not.

To update the files after local changes, pass the `pass.idea.tests.overwrite.data=true` flag:

```bash
./tests.cmd --module intellij.platform.buildScripts.tests \
    --test org.jetbrains.intellij.build.StandaloneMavenArtifactsTest -Dpass.idea.tests.overwrite.data=true
  ```

## How to use

Use `JavaCoreApplicationEnvironment` and `JavaCoreProjectEnvironment` (or their language-agnostic alternatives, `CoreApplicationEnvironment`
and `CoreProjectEnvironment`) to set up the IntelliJ Standalone Analysis environment. These classes register all services and extensions
necessary for the platform to work, so you can only register your components (e.g., custom file types, parsers and so on).

```kotlin
val environment = JavaCoreApplicationEnvironment(rootDisposable, /* unitTestMode = */ false)

// Register your language if you need to
environment.registerFileType(MyFileType, "my")
environment.registerParserDefinition(MyParserDefinition())

val projectEnvironment = JavaCoreProjectEnvironment(rootDisposable, environment)
projectEnvironment.addJarToClassPath(File("lib/library.jar"))

val project = projectEnvironment.project
val allScope = GlobalSearchScope.allScope(projectEnvironment.project)
val psiClass = JavaPsiFacade.getInstance(project).findClass("java.lang.String", allScope)
```

Currently, the `Application` is a process-wide singleton, so you cannot configure multiple `JavaCoreApplicationEnvironment` with a
different set of extensions.

## How to build artifacts locally

```bash
cd community
bazel run //build:standalone_maven_artifacts
```

The artifacts appear under `out/idea-ce/artifacts/maven-artifacts/com/jetbrains/intellij/`. The output holds the POM of each layer and the
artifact of every module the POMs reference, so it works as a local Maven repository.
# Maven Plugin Coding Agent Guidelines

Routing notes for AI coding agents working under `community/plugins/maven/`. Open the page you need; do not read everything. Repository-wide rules in the top-level `CLAUDE.md` always apply — this file adds Maven-specific overlays only.

## Module map

The plugin is split across many JPS modules. Know which one you are touching before editing.

### IDE side (runs inside IntelliJ)

| Module                          | Lives in                | Responsibility                                                              |
|---------------------------------|-------------------------|-----------------------------------------------------------------------------|
| `intellij.maven`                | `src/main/`             | Core IDE-side logic: sync, import, indices, run configurations, DOM, UI.    |
| `intellij.maven.plugin`         | `plugin/`               | Plugin descriptor (`plugin.xml`) and module content registration.           |
| `intellij.maven.model`          | `model/`                | Shared data model used by both IDE and server processes.                    |
| `intellij.maven.completion`     | `completion/`           | Completion contributors (POM, dependency coordinates).                      |
| `intellij.maven.groovy`         | `groovy/`               | GMaven / Groovy-script support.                                             |
| `intellij.maven.jps`            | `jps/`                  | JPS build-system integration (out-of-process build).                        |
| `intellij.maven.jps.ide`        | `jps/ide/`              | IDE-side bridge to the JPS module.                                          |
| `intellij.maven.proofreading`   | `proofreading/`         | Proofreading checks for POM files.                                          |
| `intellij.maven.performanceTesting` | `performanceTesting/` | Performance-test commands.                                                |

### Maven server side (runs in a separate JVM, talks to IDE over RMI)

| Module                              | Lives in                  | Responsibility                                                  |
|-------------------------------------|---------------------------|-----------------------------------------------------------------|
| `intellij.maven.server`             | (referenced via dep)      | RMI API contract between IDE and server processes.              |
| `intellij.maven.server.m3.common`   | `maven3-server-common/`   | Code shared by all Maven 3.x server implementations.            |
| `intellij.maven.server.m3.impl`     | `maven3-server-impl/`     | Maven 3.0–3.5 server implementation.                            |
| `intellij.maven.server.m36.impl`    | `maven36-server-impl/`    | Maven 3.6+ server implementation.                               |
| `intellij.maven.server.m40`         | `maven40-server-impl/`    | Maven 4.0 server implementation.                                |
| `intellij.maven.server.indexer`     | `maven-server-indexer/`   | Standalone indexer process (Apache Maven Indexer).              |
| `intellij.maven.server.telemetry`   | `maven-server-telemetry/` | OpenTelemetry shim shared by all server processes.              |
| `intellij.maven.server.eventListener` | `maven-event-listener/` | Maven extension injected into the user's build for event reporting. |
| `intellij.maven.artifactResolver.common`, `…m31` | `artifact-resolver*/` | Embedded resolver injected into the user's Maven build. |

### Test infrastructure

| Module                              | Lives in            | Use for                                                  |
|-------------------------------------|---------------------|----------------------------------------------------------|
| `intellij.maven.testFramework`      | `testFramework/`    | Base classes: `MavenTestCase`, `MavenImportingTestCase`, `MavenMultiVersionImportingTestCase`, `MavenDomTestCase`. |
| `intellij.maven.tests`              | `src/test/java/`    | Bulk of IDE-side tests.                                  |
| `intellij.maven.tests.main`         | `src/test/`         | Test entry point / resources.                            |

## Subpages

- [server-process.md](server-process.md) — Maven server JVM: **process-boundary / RMI rules**, identity, startup path, lifecycle, what to change and where, common pitfalls. Read before editing anything under `src/main/java/org/jetbrains/idea/maven/server/`, `maven*-server-impl/`, `intellij.maven.model`, or any code that crosses the IDE↔server JVM boundary.

## Build & test

- Compilation only: `./bazel.cmd build //community/plugins/maven/...`
- Run IDE-side tests (FQN required — simple class names do not match):
  - `./tests.cmd --module intellij.maven.tests --test org.jetbrains.idea.maven.<...>.SomeTest`
  - Wildcards must be quoted: `./tests.cmd --module intellij.maven.tests --test 'org.jetbrains.idea.maven.importing.*'`
- After editing any `*.iml`, `BUILD.bazel`, or `.idea/` file under this tree, run `./build/jpsModelToBazel.cmd` (repo-wide invariant — `*.iml` is the source of truth).
- After writing code, run `lint_files` on edited files and fix warnings introduced by the change.

## House rules

- User-visible strings go to the matching `*Bundle.properties` under `src/main/resources/messages/` (`MavenProjectBundle`, `MavenSyncBundle`, `MavenDomBundle`, `MavenConsoleBundle`, `MavenRunnerBundle`, `MavenIndicesBundle`, `MavenTasksBundle`, `MavenWizardBundle`, `MavenConfigurableBundle`). Never inline localizable text.
- Preserve `*.iml` files in canonical form — no reformatting, comment additions, attribute reordering, or trailing-newline edits (see top-level `CLAUDE.md`).
- Code ownership: `Java Build tools` (see `OWNERSHIP`).
- When in doubt about the dynamic-sync module split (in progress), check the current memory entry before relocating files in `importing.workspaceModel/` or `importing.tree/`.
- Avoid using String class to represent paths. When refactoring try to use java.nio.Path for paths in Intellij code, do not use java.io.File. java.io.File can be used in server code (see server-process.md)

## Routing

| Task                                            | Start in                                                                 |
|-------------------------------------------------|--------------------------------------------------------------------------|
| Project sync / import logic                     | `src/main/java/org/jetbrains/idea/maven/project/`, `importing/`          |
| Workspace-model importer / dynamic sync         | `src/main/java/org/jetbrains/idea/maven/importing/workspaceModel/`       |
| POM DOM / inspections / completion              | `dom/`, `completion/` module                                             |
| Run configurations / execution                  | `src/main/java/org/jetbrains/idea/maven/execution/`                      |
| Build-tool window / event reporting             | `src/main/java/org/jetbrains/idea/maven/buildtool/`, `maven-event-listener/` |
| Indices (local repo / remote)                   | `src/main/java/org/jetbrains/idea/maven/indices/`, `maven-server-indexer/` |
| RMI server connector / process lifecycle        | `src/main/java/org/jetbrains/idea/maven/server/`                         |
| Maven server JVM startup / lifecycle deep-dive  | [server-process.md](server-process.md)                                   |
| Adding behavior for a new Maven version         | corresponding `maven*-server-impl/` module + `m3.common` for shared bits |
| New POM wizard / starter                        | `src/main/java/org/jetbrains/idea/maven/wizards/`, `starters/`           |
| Toolchains / JDK selection                      | `src/main/java/org/jetbrains/idea/maven/toolchains/`                     |
| Test base classes                               | `testFramework/src/com/intellij/maven/testFramework/`                    |

## Updating this file

- Keep entries terse — this is a routing index, not documentation. Long-form material belongs in a sibling page under `.ai/`.
- If a row points at a path that no longer exists, fix it in the same commit as the rename — do not leave stale routing.
- Changes to this file should be a separate commit from functional work.
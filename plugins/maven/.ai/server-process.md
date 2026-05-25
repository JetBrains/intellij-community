# Maven Server Process: How It Starts, Lives, and Dies

The Maven server is the out-of-process JVM that holds the user's Maven runtime (Maven 3.x / 3.6+ / 4.0). The IDE talks to it over RMI. Almost every regression in this area comes from misunderstanding this lifecycle, so know it before editing anything under `src/main/java/org/jetbrains/idea/maven/server/`.

## Process-boundary rules

The Maven server runs in a separate JVM with the user's Maven classloader. Crossing the boundary in the wrong direction is the single most common source of regressions.

- Code in `intellij.maven.server.*` and `intellij.maven.artifactResolver.*` modules MUST NOT import IDE platform classes (`com.intellij.openapi.*`, project/PSI/VFS, etc.). Only `intellij.maven.model` and the RMI API in `intellij.maven.server` may be shared.
- Anything sent across RMI must be `Serializable` and stable — the server JVM may be running a different Maven version than the IDE expects.
- For data crossing RMI, use concrete final classes, not interfaces or abstract types. Declare fields as `ArrayList`, `HashSet`, `HashMap` rather than `List`, `Set`, `Map`, and never send the results of `Collections.emptyList()`, `List.of(...)`, `Arrays.asList(...)`, etc. The IDE and the Maven server JVM may run different Java versions (e.g. 8 vs. 25), and JDK-internal collection implementations are not guaranteed to deserialize across versions. The same applies to any custom DTO — make the class `final` so subclassing cannot smuggle a non-portable type onto the wire.
- Version-specific code belongs in the matching `m3.impl` / `m36.impl` / `m40` module; reusable Maven-3 logic belongs in `m3.common`. Never branch on Maven version inside `intellij.maven`.
- `maven-event-listener` and the artifact resolvers are injected into the *user's* Maven build. Keep their dependencies minimal and avoid anything that would clash with arbitrary user POMs.

## Identity and pooling

- One connector per `(multimoduleDirectory, jdk)`. `MavenServerManager` (application-level service) maps the multimodule root to a `MavenServerConnector`; reuse is automatic via `getConnector(project, workingDirectory)` / `getConnector(project, workingDirectory, jdk)`.
- A separate connector serves the indexer (`MavenIndexingConnectorImpl`). Do not piggy-back importer logic onto it or vice versa.
- Connectors are project-scoped (`project.isDisposed` is a connect-time failure). The manager is application-scoped.

## Startup path (read this before editing `MavenServerCMDState`)

1. `MavenServerManagerImpl.doGetConnector` → `MavenServerConnectorImpl.connect()` (executes on a pooled thread; idempotent via `myConnectStarted` CAS).
2. `StartServerTask.run` builds a `MavenRemoteProcessSupport` via `MavenRemoteProcessSupportFactory.forProject(project).create(...)` and calls `acquire(...)` to spawn the JVM.
3. `MavenServerCMDState.createJavaParameters()` resolves the right `MavenVersionAwareSupportExtension` for the distribution and asks it for: main class (`getMainClass`, default `RemoteMavenServer`, version-specific subclasses for m36 / m40), classpath (`collectClassPathAndLibsFolder`), and any version-specific tweaks.
4. The IDE adds a small RT classpath (`collectRTLibraries`: util-rt, annotations, JDOM, telemetry-context, SLF4J for pre-3.1), a fixed set of system properties (`idea.version`, `maven.embedder.version`, headless, `-Dmaven.ext.class.path=...` for the event listener), VM options from the user's `MAVEN_OPTS`, optional debug agent (`-Xdebug -Xrunjdwp` if `debugPort` is set), and optional OpenTelemetry agent.
5. Process is launched as `OSProcessHandler.Silent`. On the remote side, the `main(...)` in `RemoteMavenServer{,36,40}` reads the auth token (`MavenServerUtil.readToken()`) and registers the version-specific `Maven*ServerImpl` on the RMI registry.
6. `MavenServerConnectorImpl.startPullingLogger` schedules a fixed-delay poll that drains `ServerLogEvent`s and download notifications from the server.

## Lifecycle and shutdown

- Lazy: nothing starts until somebody calls `getConnector(...)`.
- Reuse: the existing connector is returned unless `isCompatibleWith(project, jdk, multimoduleDirectory)` is false — then the connector is shut down and a new one is started.
- Self-healing: `RemoteProcessSupport.onTerminate` is wired to `MavenServerManager.shutdownConnector(this, false)`, so a crashed server JVM is evicted from the map automatically.
- Explicit teardown: `shutdownMavenConnectors(project, predicate)` is used by VCS root changes, settings changes, and project close. It also resets `MavenProjectsManager.embeddersManager` — keep that call paired if you add more shutdown paths.
- Startup uses a project-level `CoroutineService.coroutineScope.async(Dispatchers.IO, UNDISPATCHED)` deliberately. The server must be "immortal" w.r.t. the calling coroutine — if you re-wrap this in the caller's scope, a cancelled sync orphans a half-started JVM.

## What to change, and where

- **Adding a JVM flag or system property for every Maven version** → `MavenServerCMDState.createJavaParameters()`. Gate behind a `Registry` key if it's risky.
- **Adding a flag for one Maven version only** → override the relevant `MavenVersionAwareSupportExtension` method in `m3.impl` / `m36.impl` / `m40`. Do not branch on version in `MavenServerCMDState`.
- **Adding a new RMI call** → declare it on the interface in `intellij.maven.server`, implement in every `maven*-server-impl/` (m3, m36, m40). Skipping a version silently breaks users on that Maven.
- **Supporting a new Maven major** → new `maven<N>-server-impl/` module + new `MavenVersionAwareSupportExtension` registered via the `org.jetbrains.idea.maven.versionAwareMavenSupport` EP. Do not edit existing impls.
- **Triggering a restart on a setting change** → call `shutdownMavenConnectors(project, predicate)` from the listener. Do not call `MavenServerConnector.stop` directly — that path skips embedder reset.

## What NOT to do

- Do not call `getConnectorBlocking` from new code; it is scheduled for removal. Use the suspend `getConnector(...)` and `getServer()`.
- Do not retain a `MavenServer` reference across operations. The connector may have been recreated between calls — always fetch it through the manager.
- Do not spawn the server from the EDT or from `runBlocking` in production code; the startup path expects to suspend.
- Do not assume one process per project. It is one per multimodule directory; a single project with several reactors has several servers.
- Do not add jars to `collectRTLibraries` unless you have verified the classloader impact — entries become visible to the user's Maven runtime and can clash with their plugins.
- Do not pass IDE types (`Project`, `VirtualFile`, PSI, paths as `Path`/`File` with `..` semantics) across RMI. Convert to plain serialisable DTOs in `intellij.maven.model` first, and observe the collection-type rule from [`index.md`](index.md).
- Do not start work on `acquire()` before checking `project.isDisposed`; `MavenServerConnectorBase.waitForServer` already does this — keep that contract.

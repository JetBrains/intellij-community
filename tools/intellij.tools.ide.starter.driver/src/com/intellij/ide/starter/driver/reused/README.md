# Reused IDE Runs

`@ReusedRun` runs all tests of a Driver UI test class on one IDE process. Between two tests, recovery restores the
settings and the project files. The KDoc of each class describes its own contract. This page describes the model
and its limits.

`@ReusedRun` gets its prepared context from the test context cache. The cache, the prepared state of the project
files, and the shared `system` directory are described in
[Test Context Cache](../../../../../../../../intellij.tools.ide.starter/src/com/intellij/ide/starter/testContextCache/README.md).

## Entry Point

```kotlin
val myProject = CachedProjectConfiguration(MyProjectCase)

@ReusedRun
class MyTest {
  companion object {
    @ReusedProject
    private val project = myProject
  }
}
```

Rules for a `@ReusedRun` class:

- A `@BeforeAll` method cannot take a `Driver`, because recovery can restart the IDE between tests. Put disk setup
  in `CachedProjectConfiguration.setup` and live IDE setup in `@BeforeEach`.
- `@TestMethodOrder` and `@Order` are not supported. A test must not depend on the test before it.
- A `@Nested` class is not supported. Move its tests to a top-level class.
- Every test of the class runs on the reused IDE. A test that starts its own IDE goes into another class.

A framework with its own launch flow passes a `ReusedSessionStrategy` with `@ReusedRun(sessionStrategy = ...)`.

## Lifecycle

1. The first test starts the IDE on a current test context. That context gets a copy of `config` and `plugins` from
   the cached context, and it shares the `system` directory of the cached context.
2. Before the first start, the project files go back to the prepared state of the cache entry. A test that starts
   its own IDE can share the cache entry and leave its files behind.
3. When the project has opened and indexed, the first start captures the baseline from that same IDE. The baseline
   holds `config`, `plugins`, `system/projects`, the open projects, the visible windows, and the project files.
   Every session over one cache entry shares the baseline.
4. Before each next test, recovery restores the baseline. A class with one test has no recovery.

Recovery tries the cheapest safe option first:

1. It checks that the IDE responds, and closes each window that the previous test left.
2. It saves the documents and the settings.
3. When every changed setting can reload, it closes the projects, restores the baseline, reloads the settings, and
   opens the projects again.
4. When a setting cannot reload, it closes the IDE and starts a new one from the baseline.
5. When the IDE does not respond, it force-kills the IDE and starts a new one.

When the IDE stopped during the previous test, recovery skips these steps and starts a new IDE from the baseline.

Each restart also reports a synthetic CI failure, so that the test that caused it is visible.

## What Live Recovery Cannot Rewind

Live recovery rewinds only the state on disk that a live IDE reads again. That is the project files,
`system/projects`, and the config files that `reloadChangedSettings` reloads. This other state goes into the next
test of the class:

- **The memory of the services.** A cache in memory stays across the tests of the class.
- **The config databases.** The IDE keeps `app-internal-state.db` and `updatedBrokenPlugins.db` open, and writes them
  at most every 2 minutes. No restore rewinds them, also not a restart. The plugin advertiser cache and the
  EditorConfig encoding cache live there.
- **The lock and the tokens of the process.** No restore touches `config/.lock`.
- **The SDK table and the global libraries.** The IDE reads `jdk.table.xml` and `applicationLibraries.xml` only at
  its start. Recovery rewinds the files, but an SDK that a test adds stays in memory until a restart.

A change to a file in `nonReloadableSettingsFileSpecs`, such as `disabled_plugins.txt`, causes a restart. That list is
not complete. Another file that the IDE reads only at its start gets a reload that has no effect.

## The Project Files And The Build Output

The baseline holds the opened state of the project files. The session captures it after the open and the import, so
a recovery does not import the project again. It leaves out the build roots. It copies only the files that changed
since the prepared state, and reads the other files from the prepared snapshot.

| Start | Rewinds to | Sources and `.idea` | `build`, `target`, `.gradle`, `.kotlin` |
|---|---|---|---|
| The first start | the prepared state | restored | restored |
| The next tests | the opened state | restored | kept from the test before |

So a test builds on the output of the test before it:

- Do not assert that the build output is absent, or that a task is not `UP-TO-DATE`.
- Do not assert on `Reusing configuration cache` or `Configuration cache entry stored`.
- A test that writes `.gradle/config.properties` must restore it. The Gradle JVM `#GRADLE_LOCAL_JAVA_HOME` reads it.
- A test that needs a clean build must clean the project, for example with the Gradle `clean` task.

A build daemon that outlives a test keeps its memory.

The restore writes with plain file-system calls, so the IDE does not see it. After a restore that wrote to the
project files, recovery refreshes the VFS before the next test.

## The Shared System Directory After A Crash

Recovery never empties the shared `system` directory. After a force kill or a crash, the next IDE opens it as after a
crash, and keeps the VFS and the indexes. A replacement IDE starts only when every IDE process of the session is dead
and the recovery worker has stopped. Otherwise recovery fails the test, because two IDEs must not use one system
directory.

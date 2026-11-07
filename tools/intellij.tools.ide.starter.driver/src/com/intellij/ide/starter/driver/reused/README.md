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
3. When every changed setting can reload, it closes the projects, restores the baseline, reloads the settings, rolls
   back the cache store, and opens the projects again. See [The Cache Store](#the-cache-store).
4. When a setting cannot reload, or the cache store cannot roll back, it closes the IDE and starts a new one from the
   baseline.
5. When the IDE does not respond, it force-kills the IDE and starts a new one.

When the IDE stopped during the previous test, recovery skips these steps and starts a new IDE from the baseline.

Each restart also reports a synthetic CI failure, so that the test that caused it is visible.

## What Live Recovery Cannot Rewind

Live recovery rewinds only the state on disk that a live IDE reads again. That is the project files,
`system/projects`, and the config files that `reloadChangedSettings` reloads. This other state goes into the next
test of the class:

- **The memory that no settings component holds.** Recovery reloads each settings component whose file or whose
  cache store key changed, so the memory of that component goes back to the baseline. Other memory stays across the
  tests of the class. A lazy cache, a registered listener, and a counter are such memory.
- **The config databases.** The IDE keeps `app-internal-state.db` and `updatedBrokenPlugins.db` open, and writes them
  at most every 2 minutes. A restart copies the databases from the cached context. `app-internal-state.db` holds the
  cache store, which has its own rules. See [The Cache Store](#the-cache-store).
- **The extracted plugin resources.** `ExtensionsRootType` extracts them into `config/extensions` on the first use.
  Live recovery keeps the files. When it rolls back the record of an extraction, the IDE extracts the files again.
- **The lock and the tokens of the process.** No restore touches `config/.lock`.
- **The SDK table and the global libraries.** The global workspace model reads `jdk.table.xml` and
  `applicationLibraries.xml` only at the IDE start. Recovery rewinds the files, but an SDK that a test adds stays in
  memory until a restart. One SDK in the shared setup is enough, because every test then sees the same SDK. A class
  whose tests need different SDKs registers the SDK of each test in `@BeforeEach`. A test that asserts that an SDK
  is absent cannot use `@ReusedRun`.

A change to a file in `nonReloadableSettingsFileSpecs`, such as `disabled_plugins.txt`, causes a restart. That list is
not complete. Another file that the IDE reads only at its start gets a reload that has no effect.

## The Cache Store

The cache store lives in `app-internal-state.db`. The IDE holds the database open, so no file baseline shows that
state. Live recovery lists the keys of the store at the IDE start and after each test. The recovery log names each
changed key.

Two kinds of code write to the store. Recovery handles each kind in its own way.

| The writer | Its key | What live recovery does |
|---|---|---|
| An application component with the `$CACHE_FILE$` storage, as `SearchEverywhereConfiguration` has | `<plugin id>.<component name>` | It writes the start value back and reloads the component. |
| Code that tags a setting `CacheTag`, as `EditorConfigEncodingCache` does | the key that this code chose | It keeps the value that the test left. |

`CacheTag` is `com.intellij.platform.settings.CacheTag`, a tag of the settings API. There is no component to reload,
and such code keeps its own copy in memory. A rollback in the store would not reach that copy. So live recovery
treats the data of a `CacheTag` key as the memory that no settings component holds, and keeps it.

Live recovery restarts the IDE when it cannot roll a changed key back:

- The component of the key cannot reload.
- The rollback does not hold, because the component wrote the value of the test again.
- The IDE cannot list the cache store, or cannot list its `$CACHE_FILE$` components.

A restart on every changed key is not an option. `TypeScriptExternalDefinitionsRegistry` and `GradleJvmSupportMatrix`
change without a test, so almost every class would restart the IDE and report a synthetic CI failure.

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

# Test Context Cache

The test context cache prepares a project once per process, and gives each test a current test context of its own
over it. The KDoc of each class describes its own contract. This page describes the model and its limits.

There are two ways to use a prepared context:

- **A test that starts its own IDE.** It calls `Starter.newTestContextFromCache(configuration)`. This page describes it.
- **A `@ReusedRun` class.** One IDE serves all tests of the class. See
  [Reused IDE Runs](../../../../../../../intellij.tools.ide.starter.driver/src/com/intellij/ide/starter/driver/reused/README.md).

One configuration instance can serve both ways, in any order.

## Entry Point

```kotlin
val myProject = CachedProjectConfiguration(MyProjectCase)

@Test
fun myTest() {
  Starter.newTestContextFromCache(myProject)
    .runIdeWithDriver()
    .useDriverAndCloseIde { /* ... */ }
}
```

The cache key is the `CachedProjectConfiguration` instance, the target, and the IDE run mode. So one configuration in
the monolith and the split mode has two cache entries. A new instance is a new cache entry, which
prepares and indexes the project again. Share one instance when tests must share one prepared
context.

`CachedProjectConfiguration.setup` runs once, when the cache entry is created. It prepares the config, the plugins,
the VM options, and the project files. It must not start an IDE. The default setup does nothing.

The cached context directory is named after the project, see `ProjectInfoSpec.getName()`. Two cache entries of
one project differ only in a suffix.

Each test gets a current test context of its own. It has its own copy of `config`, `plugins`, and the VM options, so a
change that one test makes to them does not go into the next test. Its directory is named after the test class.

## The Prepared State Of The Project Files

The cache entry captures the prepared state right after `setup`, before any IDE start. It holds the whole project,
the build roots included. It lives as long as the process.

`Starter.newTestContextFromCache()` rewinds the project directory to the prepared state before each test. So each test starts from
the state that `setup` left, whatever the test before it did. This also applies to `.idea`, `workspace.xml` included,
and to a test that failed. The build roots `build`, `target`, `.gradle`, and `.kotlin` are restored too, so each test
compiles from the prepared state:

- The prepared snapshot copies the whole project. Do not build the project in `setup`.
- A Gradle or Kotlin daemon can outlive the IDE of the test before. On Windows, the restore of a file that it holds
  fails. A daemon that outlives a test keeps its memory.

The restore writes with plain file-system calls, so the IDE does not see it. A restore that wrote to the project
files refreshes the VFS when the next IDE starts, see https://youtrack.jetbrains.com/issue/AT-4588.

A test that starts its own IDE has none of the limits of live recovery, because no IDE lives across two tests.

## Shared System Directory

All current test contexts of one cached project use the `system` directory of the cached context
(`IDEDataPaths.sharedSystemDir`). The IDE gets it as `idea.system.path`, so the indexes and the VFS of one launch serve
the next. The IDE processes of one cached project run one at a time.

The `system` directory is not copied. The IDE writes to a live VFS during the copy, so the copy is not consistent.
Only a stopped VFS is safe to copy, and a stop for each reuse would cost a warm-up launch. The path is the real
directory, not a symlink, so it needs no special case on Windows, in a delete, or in a walk.

A failed test does not drop the cache entry. The next test rewinds the project files and keeps the `system` directory.
After a crash, the next IDE opens it as after a crash, and keeps the VFS and the indexes.

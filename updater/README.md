The updater is a self-contained `.jar` file that the IDE self-update mechanism is built on.

It has two jobs:
- **building patches**: at build time, it compares two IDE distributions and packs the difference into a patch file;
- **applying patches**: on a user's machine, it updates an existing installation in place, using that patch file.

The same code does both. Every patch file is the updater `.jar` with the patch data added to it,
so a patch can always apply itself, and the code that applies it matches the code that built it.


### Role in the IDE update process

1. The build infrastructure builds the new distribution and runs the updater in `create` mode against several earlier builds.
   The resulting patch files are published next to the full distributions.
2. The IDE (`UpdateChecker`, `PlatformUpdateDialog`) finds out about a new build from the update metadata. If it finds a patch or a chain
   of patches from the current build to the new one, it downloads them into `<system>/tmp/patch-update/`
   (see `UpdateInstaller.downloadPatchChain`).
3. `UpdateInstaller.preparePatchCommand` puts together the command line that runs the updater:
   - It uses the IDE's own Java runtime. If that runtime sits inside the installation directory, it is copied to the temp directory
     first, because the patch may replace it.
   - The patch `.jar` goes on the classpath, and the main class is `com.intellij.updater.Runner`.
   - The mode is `install` for one patch and `batch-install` for a chain.
   - `-Didea.updater.log` points at the IDE log directory.
   - On Windows, if the installation directory is not writable, the command is wrapped in `launcher.exe`/`elevator.exe`
     to get elevated rights.
4. The IDE exits. The restarter runs the command, the updater patches the installation, and the restarter starts the updated IDE.
5. On the next start, the IDE checks `idea_updater_error.log` (see `UpdateCheckerService`). If the file is not empty, the IDE
   records the update as failed.

Other tools can apply patches without the IDE. The `Bootstrap` entry point (`java -jar <patch>.jar <installation>`) is meant
for tools such as JetBrains Toolbox App, which copy the installation, then patch the copy (see "Applying a patch" below).


### Patch file format

A patch is a `.jar` (zip) file that contains:
- the updater classes, plus the bundled dependencies it needs at runtime (JNA, used for Windows-specific post-update tasks);
- `patch-file.zip`, which holds the patch itself:
  - `.patch-info`: a binary header with the old and new version descriptions, the patch root, flags, and the list of actions;
  - one entry for each created or updated file. An entry is either the whole file or a binary diff
    (bsdiff-based, see `ie.wombat.jbdiff`).

There are four kinds of actions (subclasses of `PatchAction`). Each action records a CRC32-based checksum (see `Digester`) of the file it expects:
- `CreateAction`: add a new file, directory, or symlink.
- `UpdateAction`: replace a file with a new version, using either a binary diff or the full file. If the same file content
  shows up at a different path, the action moves the file instead of shipping it again.
- `DeleteAction`: remove a file or directory.
- `ValidateAction`: check that a file has not changed. Only strict patches use it.


### Building a patch (`create`)

```
java -cp updater-full.jar com.intellij.updater.Runner create \
  <old_version> <new_version> <old_folder> <new_folder> <patch_file> \
  [ignored=f1;f2;...] [critical=...] [strictfiles=...] [optional=...] [delete=regex1;regex2;...] \
  [--strict] [--root=<dir>] [--cache-dir=<dir>] [--timeout=<seconds>] [--jar=<updater.jar>]
```

The updater:
1. computes checksums for every file in both folders (skipping `ignored` ones);
2. works out the difference: which files were created, deleted, updated, or moved, and which are the same in both;
3. turns the difference into a list of actions;
4. builds a diff for each updated file, in parallel. The whole file goes into the patch instead if the file is `critical`,
   if the diff is not smaller than the file, or if building the diff takes longer than `--timeout` allows;
5. writes `patch-file.zip`, then copies the updater `.jar` (the running one, or the one given with `--jar=`)
   and adds `patch-file.zip` to the copy, which becomes `<patch_file>`.

File sets (paths are relative, separated by `;`):
- `ignored`: these files are left out of the patch.
- `critical`: these files are included in full, so they can be replaced even if they were modified in the installation.
- `strictfiles`: a mismatch in any of these files blocks the installation; the conflict cannot be ignored.
- `optional`: these files may be absent from the installation.
- `delete`: regular expressions for paths that can be deleted without asking the user.

Flags:
- `--strict`: the patch also validates unchanged files and deletes unknown ones, so the patched installation
  exactly matches `<new_folder>`.
- `--root=<dir>`: the patch is applied to this subdirectory, given relative to the folders (e.g., `Contents/` in macOS `.app` bundles).
- `--cache-dir=<dir>`: diffs are cached in this directory and reused, which speeds things up when building
  several patches to the same new build.
- `--timeout=<T>`: a time limit, in seconds, for building the diff of a single file.

Run `Runner` without arguments to see usage notes.


### Applying a patch (`install`, `batch-install`, `apply`)

```
java -cp <patch>.jar com.intellij.updater.Runner install <installation_dir> [--no-backup]
java -cp <last-patch>.jar com.intellij.updater.Runner batch-install <installation_dir> <patch1>:<patch2>:... [--no-backup]
java -cp <patch>.jar com.intellij.updater.Runner apply <installation_dir> [--force-replace] [--no-backup]
java [-Dno.backup=true] -jar <patch>.jar <installation_dir>
```

Modes:
- `install`: applies the patch that contains the running updater and shows a Swing UI (`SwingUpdaterUI`). The UI shows progress
  and asks the user how to resolve conflicts. This is the mode the IDE uses.
- `batch-install`: applies a chain of patches one after another, also with the Swing UI. The IDE uses it to update across several builds.
- `apply`: applies the patch with a console UI (`ConsoleUpdaterUI`) and nothing interactive. Any conflict makes it fail,
  unless `--force-replace` is given, which resolves conflicts by replacing files where that is allowed.
- `java -jar <patch>.jar <dir>` (`Bootstrap`, the `Main-Class` of the `.jar`): runs `apply <dir> --force-replace`.
  Setting `-Dno.backup=true` adds `--no-backup`, for callers that make their own copy of the installation first.

Applying a patch happens in these steps:
1. **Extract**: `patch-file.zip` is unpacked into a temp directory.
2. **Validate**: the updater checks that the installation directory matches the patch root, reads `.patch-info`, and checks every action
   against the installation. It looks at file access and expected checksums, and on Windows it also checks for files locked by
   running processes. Any problem becomes a `ValidationResult` (info, conflict, or error). The UI resolves it by choosing an option:
   ignore, keep, replace, delete, or kill the process.
3. **Back up**: unless `--no-backup` is given, the updater saves every file it is about to change to a temp backup directory.
   For `batch-install`, it copies the whole installation. Even with `--no-backup`, it still backs up files that are moved,
   because the move needs a source to copy from.
4. **Apply**: the actions run in order. Deletions come first, so that names differing only in case do not collide.
5. **Revert**: if something fails after the installation was partly modified, the updater restores the backup. If there is no backup,
   it tells the user to reinstall the product.
6. **Post-update tasks** (`PostUpdateTasks`): on macOS, the app bundle icon is refreshed; on Windows, the registry entries and shortcuts
   are updated; on Linux, desktop entries are updated (for builds 253 and later).
7. **Clean up**: temp files are deleted.

The process exits with code `0` on success, `1` on failure, and `2` on an internal error.
Logs go to `idea_updater.log` (appended) and `idea_updater_error.log` (errors from the last run only). They are written to the directory
given by the `idea.updater.log` system property, or to `java.io.tmpdir` if it is not set.
Arguments can also be read from a file, using `@<file>` with one argument per line.


### Building and testing

The project is intentionally not a part of the enclosing repository, because it has distinct lifecycle.
Use the included Gradle wrapper for building and testing:

```
./gradlew fatJar   # build/libs/updater-full.jar
./gradlew test
```

The code targets older JRE than the platform. A patch runs on the old IDE's runtime, so the updater must work there.

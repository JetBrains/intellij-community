# libghostty-vt

The VT engine shared library consumed by `com.intellij.terminal.emulator.impl.ghostty.GhosttyTerminalEmulator`.

- Source: https://github.com/JetBrains/ghostty
- Storage: https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/libghostty-vt

The library builds are bundled into the `intellij.terminal` plugin as
`libghostty-vt/<os>-<arch>/` — see `CommunityRepositoryModules.kt`.
When running from sources (including tests), it's downloaded on the fly — see `LibGhosttyVtLocator.kt`.

## Syncing the fork with upstream

Run these commands in a clone of `JetBrains/ghostty`.
The `+` prefix lets Git replace the moving `tip` tag.

1. If `upstream` is missing from `git remote -v`, add it:

   ```sh
   git remote add upstream https://github.com/ghostty-org/ghostty.git
   ```

2. Fetch the branches, release tags, and `tip`:

   ```sh
   git fetch origin
   git fetch upstream
   git fetch upstream 'refs/tags/v*:refs/tags/v*' '+refs/tags/tip:refs/tags/tip'
   ```

3. Merge both remote `main` branches into the local branch:

   ```sh
   git switch main
   git merge --no-edit origin/main
   git merge --no-edit upstream/main
   ```
   When the fork has no commits of its own, the merge is a fast-forward. Otherwise, Git makes a merge commit.
   If Git reports conflicts, resolve them and run `git commit --no-edit`.

4. Push `main`, the release tags, and `tip` to the fork:

   ```sh
   git push origin main 'refs/tags/v*:refs/tags/v*' '+refs/tags/tip:refs/tags/tip'
   ```

## Updating the bundled library

1. Run the `Terminal: libghostty-vt / Publish` TeamCity configuration on a tag or a branch.
   For example, use a release tag such as `v1.4.0` or the rolling `tip` tag, which marks
   the last commit on the `main` branch that passed the tests (a prerelease build).
   The configuration builds the library and uploads it to the storage.
2. Set the `libGhosttyVtVersion` property in
   [dependencies.properties](../../../build/dependencies/dependencies.properties) to the uploaded version.
   Take the version from the uploaded file name: `libghostty-vt-<version>.zip.zst`.

## Using a custom build

To use a custom build of libghostty-vt, set `-Dij.terminal.libghostty-vt.lib.root=<libghostty-vt root>`.
The directory must hold the `<os>-<arch>` subdirectory of the current platform:

```text
<libghostty-vt root>/
├── linux-aarch64/libghostty-vt.so
├── linux-x86_64/libghostty-vt.so
├── macos-aarch64/libghostty-vt.dylib
├── macos-x86_64/libghostty-vt.dylib
├── windows-aarch64/ghostty-vt.dll
└── windows-x86_64/ghostty-vt.dll
```

### "Cannot open library" on macOS

A build that you download with a browser can fail to load:

```text
java.lang.IllegalArgumentException: Cannot open library: <libghostty-vt root>/macos-aarch64/libghostty-vt.dylib
```

The browser marks the downloaded file with the `com.apple.quarantine` file attribute.
When you extract the downloaded archive, the extracted files get the attribute too.
macOS forbids loading a quarantined library that is not notarized.

To fix it, remove the quarantine attribute from the `<libghostty-vt root>` directory recursively:

```sh
xattr -dr com.apple.quarantine <libghostty-vt root>
```

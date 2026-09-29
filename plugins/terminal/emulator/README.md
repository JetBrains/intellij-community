# libghostty-vt

The VT engine shared library consumed by `com.intellij.terminal.emulator.impl.ghostty.GhosttyTerminalEmulator`.

- Source: https://github.com/JetBrains/ghostty
- Storage: https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/libghostty-vt

The library builds are bundled into the `intellij.terminal` plugin as
`libghostty-vt/<os>-<arch>/` — see `CommunityRepositoryModules.kt`.
When running from sources (including tests), it's downloaded on the fly — see `LibGhosttyVtLocator.kt`.

## Syncing the fork with upstream

Run these commands in a clone of `JetBrains/ghostty`:

1. Add the upstream remote once:
   ```
   git remote add upstream https://github.com/ghostty-org/ghostty.git
   ```
2. Fetch both remotes.
   Upstream moves the `tip` tag on each commit that passed tests, so the tag needs a forced fetch:
   ```
   git fetch origin
   git fetch upstream
   git fetch upstream '+refs/tags/tip:refs/tags/tip'
   ```
3. Merge the fork and upstream changes into the local `main` branch:
   ```
   git switch main
   git merge --no-edit origin/main
   git merge --no-edit upstream/main
   ```
   When the fork has no commits of its own, the merge is a fast-forward. Otherwise, Git makes a merge commit.
   If Git reports conflicts, resolve them and run `git commit --no-edit`.
4. Push the branch and the tag to the fork:
   ```
   git push origin main
   git push --force origin tip
   ```

## Updating the library

1. Run the `Terminal: libghostty-vt / Publish` TeamCity configuration on the `tip` branch.
   The `tip` tag marks the last upstream commit that passed the tests.
   The configuration builds the library and uploads it to the storage.
2. Set the `libGhosttyVtVersion` property in
   [dependencies.properties](../../../build/dependencies/dependencies.properties) to the uploaded version.
   Take the version from the uploaded file name: `libghostty-vt-<version>.zip.zst`.

## Using a custom build

To use a custom build of libghostty-vt, set `-Dij.terminal.libghostty-vt.lib.root=<directory>` to a directory
holding the needed `<os>-<arch>` subdirectory with the library file.

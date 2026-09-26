# libghostty-vt

The VT engine shared library consumed by `com.intellij.terminal.emulator.impl.ghostty.GhosttyTerminalEmulator`.

- Source: https://github.com/JetBrains/ghostty
- Storage: https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/libghostty-vt

The library builds are bundled into the `intellij.terminal` plugin as
`libghostty-vt/<os>-<arch>/` — see `CommunityRepositoryModules.kt`.
When running from sources (including tests), it's downloaded on the fly — see `LibGhosttyVtLocator.kt`.

## Updating the library

1. Run the `Terminal: libghostty-vt / Test & Build All platforms` TeamCity configuration against
   the desired `JetBrains/ghostty` revision.
2. Upload the resulting `libghostty-vt.zip.zst` to the storage under `libghostty-vt/<version>/`.
   This is a manual step for now.
3. Set the `libGhosttyVtVersion` property in
   [dependencies.properties](../../../build/dependencies/dependencies.properties) to that version.

## Using a custom build

To use a custom build of libghostty-vt, set `-Dij.terminal.libghostty-vt.lib.root=<directory>` to a directory
holding the needed `<os>-<arch>` subdirectory with the library file.

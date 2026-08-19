// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Some tests download the real libghostty-vt archive into the build-dependencies cache,
 * so network access is needed.
 */
internal class LibGhosttyVtLocatorTest {
  @ParameterizedTest
  @CsvSource(
    "WINDOWS, X86_64, windows-x86_64/ghostty-vt.dll",
    "WINDOWS, AARCH64, windows-aarch64/ghostty-vt.dll",
    "MACOS, X86_64, macos-x86_64/libghostty-vt.dylib",
    "MACOS, AARCH64, macos-aarch64/libghostty-vt.dylib",
    "LINUX, X86_64, linux-x86_64/libghostty-vt.so",
    "LINUX, AARCH64, linux-aarch64/libghostty-vt.so",
  )
  fun libraryPath(os: GhosttyVtOs, arch: GhosttyVtArch, expectedPath: String) {
    assertThat(LibGhosttyVtLocator.libraryPath(os, arch)).isEqualTo(expectedPath)
  }

  /**
   * The file names in [GhosttyVtOs] are hand-written for all platforms at once, so
   * nothing verifies them against the actual platform conventions except this: on
   * whichever platform the test runs, the name has to be the one the JVM itself
   * would load.
   */
  @Test
  fun `library file name of the current platform follows mapLibraryName`() {
    assertThat(LibGhosttyVtLocator.currentPlatformLibraryPath())
      .endsWith("/${System.mapLibraryName(GHOSTTY_VT_LIB_BASE_NAME)}")
  }

  /**
   * A distribution bundles only the build matching its own platform, so a missing or
   * misnamed file in the published archive surfaces only when that platform's
   * distribution is built (`NativeBinaryDownloader.getLibGhosttyVt` checks it).
   * Checking all the platforms from any single machine catches it earlier.
   */
  @Test
  fun `library file of every platform is present in the published archive`() {
    val libRoot = LibGhosttyVtLocator.getOrDownloadLibRoot()
    GhosttyVtOs.entries.forEach { os ->
      GhosttyVtArch.entries.forEach { arch ->
        val libraryPath = LibGhosttyVtLocator.libraryPath(os, arch)
        assertThat(libRoot.resolve(libraryPath)).isRegularFile()
      }
    }
  }

  /**
   * The other direction of the check above: an archived build directory nothing looks
   * up is dead weight — a leftover of a renamed platform, or a build added in the
   * expectation that something would pick it up. The check above cannot see it, being
   * satisfied as soon as the names it *does* ask for are present.
   *
   * The distribution build derives the same `<os>-<arch>` names independently, in
   * `org.jetbrains.intellij.build.NativeBinaryDownloader.getLibGhosttyVt`, so an orphan
   * here is also one that nothing gets packaged from.
   */
  @Test
  fun `every library directory in the published archive belongs to a known platform`() {
    val expected = GhosttyVtOs.entries.flatMapTo(HashSet()) { os ->
      GhosttyVtArch.entries.map { arch -> LibGhosttyVtLocator.libraryPath(os, arch).substringBefore('/') }
    }
    val present = LibGhosttyVtLocator.getOrDownloadLibRoot().listDirectoryEntries().filter { it.isDirectory() }.map { it.name }

    assertThat(present.filterNot { it in expected })
      .describedAs("library directories in the libghostty-vt archive that no platform looks up")
      .isEmpty()
  }

  /**
   * The zero-configuration path: with no property set, a from-sources run downloads
   * the library itself.
   */
  @Test
  fun `findLibraryFile downloads the library when running from sources`() {
    assumeTrue(LibGhosttyVtLocator.shouldDownload())
    runWithLibRootOverride(null) {
      assertThat(LibGhosttyVtLocator.findLibraryFile()).isRegularFile()
    }
  }

  /**
   * The override points at a directory no other lookup could resolve, so passing
   * proves the property takes precedence.
   */
  @Test
  fun `findLibraryFile honors the lib root override property`(@TempDir overriddenLibRoot: Path) {
    val libFile = overriddenLibRoot.resolve(LibGhosttyVtLocator.currentPlatformLibraryPath())
    Files.createDirectories(libFile.parent)
    Files.createFile(libFile)
    runWithLibRootOverride(overriddenLibRoot.toString()) {
      assertThat(LibGhosttyVtLocator.findLibraryFile()).isEqualTo(libFile)
    }
  }

  @Test
  fun `findLibraryFile mentions the override property when the overridden file is missing`(@TempDir overriddenLibRoot: Path) {
    runWithLibRootOverride(overriddenLibRoot.toString()) {
      assertThatThrownBy { LibGhosttyVtLocator.findLibraryFile() }
        .isInstanceOf(IOException::class.java)
        .hasMessageContaining(GHOSTTY_VT_LIB_ROOT_PROPERTY)
    }
  }

  /**
   * Runs [block] with [GHOSTTY_VT_LIB_ROOT_PROPERTY] set to [libRoot] (cleared when
   * null), restoring the previous value afterward.
   */
  private fun runWithLibRootOverride(libRoot: String?, block: () -> Unit) {
    val previous = if (libRoot == null) System.clearProperty(GHOSTTY_VT_LIB_ROOT_PROPERTY)
                   else System.setProperty(GHOSTTY_VT_LIB_ROOT_PROPERTY, libRoot)
    try {
      block()
    }
    finally {
      if (previous == null) System.clearProperty(GHOSTTY_VT_LIB_ROOT_PROPERTY) else System.setProperty(GHOSTTY_VT_LIB_ROOT_PROPERTY, previous)
    }
  }
}

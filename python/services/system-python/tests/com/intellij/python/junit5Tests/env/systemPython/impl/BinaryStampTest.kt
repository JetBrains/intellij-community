// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.python.community.services.systemPython.impl.binaryStamp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

/**
 * `binaryStamp` tells the caller whether an interpreter is the same file as the one it read before, so the answer
 * must hold on every operating system and for every shape of path an interpreter comes in. See PY-88315.
 */
internal class BinaryStampTest {

  @Test
  fun testSameFileKeepsItsStamp(@TempDir dir: Path) {
    val python = dir.resolve("python3").also { it.writeText("one") }
    assertThat(python.binaryStamp()).isNotNull.isEqualTo(python.binaryStamp())
  }

  @Test
  fun testNewContentChangesTheStamp(@TempDir dir: Path) {
    val python = dir.resolve("python3").also { it.writeText("one") }
    val before = python.binaryStamp()

    python.writeText("a longer text")
    assertThat(python.binaryStamp()).describedAs("The size changed").isNotEqualTo(before)
  }

  @Test
  fun testNewTimeChangesTheStamp(@TempDir dir: Path) {
    val python = dir.resolve("python3").also { it.writeText("one") }
    val before = python.binaryStamp()

    // A replaced binary of the same size still has a time of its own
    Files.setLastModifiedTime(python, FileTime.fromMillis(0))
    assertThat(python.binaryStamp()).describedAs("The time changed").isNotEqualTo(before)
  }

  @Test
  fun testMissingFileHasNoStamp(@TempDir dir: Path) {
    assertThat(dir.resolve("no-such-python").binaryStamp()).isNull()
  }

  /**
   * The chain a distribution builds: `python` -> `python3` -> `python3.11`. The user then installs 3.13, and the
   * middle link points at it. The first link is untouched, so only the target tells the two apart.
   */
  @Test
  fun testStampFollowsTheWholeSymlinkChain(@TempDir dir: Path) {
    val python311 = dir.resolve("python3.11").also { it.writeText("3.11") }
    val python313 = dir.resolve("python3.13").also { it.writeText("3.13, a longer one") }
    val python3 = dir.resolve("python3").symlinkTo(python311)
    val python = dir.resolve("python").symlinkTo(python3)

    assertThat(python.binaryStamp()).describedAs("The stamp of the target").isEqualTo(python311.binaryStamp())

    Files.delete(python3)
    dir.resolve("python3").symlinkTo(python313)

    assertThat(python.binaryStamp())
      .describedAs("The chain now ends at another file, so the stamp must change")
      .isEqualTo(python313.binaryStamp())
      .isNotEqualTo(python311.binaryStamp())
  }

  /**
   * A python from the Windows Store is a reparse point with the `IO_REPARSE_TAG_APPEXECLINK` tag. Windows gives no
   * attributes for it, so the answer is `null` rather than an exception.
   */
  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun testWindowsStorePythonHasNoStamp() {
    val localAppData = System.getenv("LOCALAPPDATA")
    Assumptions.assumeTrue(localAppData != null, "No LOCALAPPDATA")
    val windowsApps = Path.of(localAppData, "Microsoft", "WindowsApps")
    val storePython = try {
      windowsApps.listDirectoryEntries("python*.exe").firstOrNull()
    }
    catch (e: IOException) {
      Assumptions.abort("Cannot read $windowsApps : ${e.message}")
    }
    Assumptions.assumeTrue(storePython != null, "No python from the Windows Store in $windowsApps")

    // The value is not the point. The call must answer instead of throwing.
    storePython!!.binaryStamp()
  }

  /** Creates the link, or ends the test when the operating system does not let this user create one. */
  private fun Path.symlinkTo(target: Path): Path =
    try {
      Files.createSymbolicLink(this, target)
    }
    catch (e: IOException) {
      Assumptions.abort("Cannot create a symbolic link: ${e.message}")
    }
    catch (e: UnsupportedOperationException) {
      Assumptions.abort("The file system has no symbolic links: ${e.message}")
    }
}

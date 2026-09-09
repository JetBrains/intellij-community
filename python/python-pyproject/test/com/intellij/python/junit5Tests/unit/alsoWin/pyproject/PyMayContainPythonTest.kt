// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.jetbrains.python.venvReader.VirtualEnvReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

/**
 * Pins the filter that guards `findPythonInPythonRoot` in the subtree load of PY-91841.
 *
 * The filter must answer a superset. A `false` for a directory that holds an interpreter would stop the
 * load from pruning an environment, and the VFS would take thousands of files of it.
 *
 * The filter reads the layout of one system, the one that the guarded method reads for the same path. Each
 * case therefore forces the family of the system, so the answer never depends on the host of the test.
 */
internal class PyMayContainPythonTest {

  private val someDirectory = Path("project", "member")

  private fun posix(vararg childNames: String): Boolean =
    VirtualEnvReader(isWindows = false).mayContainPython(someDirectory, childNames.asSequence())

  private fun windows(vararg childNames: String): Boolean =
    VirtualEnvReader(isWindows = true).mayContainPython(someDirectory, childNames.asSequence())

  @Test
  fun testTheDirectoryOfInterpretersPasses() {
    assertThat(posix("bin")).describedAs("the posix layout").isTrue()
    assertThat(windows("Scripts")).describedAs("the windows layout").isTrue()
  }

  /**
   * `findPythonInPythonRoot` resolves the directory of interpreters with `Path.resolve`, and a filesystem
   * that ignores the case answers `resolve("bin")` with a directory named `Bin`. The default volume of
   * macOS does that, so the filter must accept the other case or it stops being a superset.
   */
  @Test
  fun testTheCaseOfTheDirectoryNameDoesNotMatter() {
    for (name in listOf("Bin", "BIN")) {
      assertThat(posix(name)).describedAs("a directory that holds '$name'").isTrue()
    }
    for (name in listOf("scripts", "SCRIPTS")) {
      assertThat(windows(name)).describedAs("a directory that holds '$name'").isTrue()
    }
  }

  /** The second way: the binary lies directly in the directory, as in the root of an installation. */
  @Test
  fun testABinaryInTheDirectoryPasses() {
    for (name in listOf("python", "python3", "python3.11", "pythonw", "pypy", "pypy3.10", "python3.13t")) {
      assertThat(posix(name)).describedAs("the posix layout holds '$name'").isTrue()
    }
    for (name in listOf("python.exe", "pythonw.exe", "python3.11.exe", "python_d.exe")) {
      assertThat(windows(name)).describedAs("the windows layout holds '$name'").isTrue()
    }
  }

  /**
   * The pattern of the layout decides, and the pattern of posix reads the case. `findInterpreter` matches a
   * name the same way, so a file named `Python3` reaches no interpreter there either.
   */
  @Test
  fun testTheCaseOfABinaryNameFollowsTheLayout() {
    assertThat(posix("Python3")).describedAs("the posix pattern reads the case").isFalse()
    assertThat(windows("PYTHON.EXE")).describedAs("the windows pattern ignores the case").isTrue()
  }

  /** The layout of the other system does not count, because the method reads one layout for one path. */
  @Test
  fun testTheLayoutOfTheOtherSystemDoesNotCount() {
    assertThat(posix("Scripts", "python.exe")).describedAs("windows names under the posix layout").isFalse()
    assertThat(windows("python")).describedAs("a posix binary name under the windows layout").isFalse()
  }

  @Test
  fun testAnOrdinaryDirectoryDoesNotPass() {
    assertThat(posix("src", "tests", "README.md", "pyproject.toml")).isFalse()
    assertThat(posix()).describedAs("no child at all").isFalse()
  }

  /**
   * The filter reads the patterns of the layout, so a name that only starts like a binary does not pass.
   * `findInterpreter` would reject the same name.
   */
  @Test
  fun testANameThatOnlyStartsLikeABinaryDoesNotPass() {
    assertThat(posix("python_helper")).isFalse()
    assertThat(posix("pythonrc")).isFalse()
    assertThat(posix("bins")).describedAs("`bins` is not `bin`").isFalse()
  }
}

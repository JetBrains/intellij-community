// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.jetbrains.python.venvReader.VirtualEnvReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the filter that guards `findPythonInPythonRoot` in the subtree load of PY-91841.
 *
 * The filter must answer a superset. A `false` for a directory that holds an interpreter would stop the
 * load from pruning an environment, and the VFS would take thousands of files of it.
 *
 * The filter covers both layouts at once, so a name of either one passes on every operating system.
 */
internal class PyMayContainPythonTest {

  private val reader = VirtualEnvReader()

  private fun mayContain(vararg childNames: String): Boolean = reader.mayContainPython(childNames.asSequence())

  @Test
  fun testADirectoryOfInterpretersPasses() {
    assertThat(mayContain("bin")).describedAs("the posix layout").isTrue()
    assertThat(mayContain("Scripts")).describedAs("the windows layout").isTrue()
  }

  /**
   * `findPythonInPythonRoot` resolves the directory of interpreters with `Path.resolve`, and the filesystem
   * of macOS and of Windows resolves a name without the case. A directory named `Bin` answers to
   * `resolve("bin")` there, so the filter must accept it or it stops being a superset.
   */
  @Test
  fun testTheCaseOfTheDirectoryNameDoesNotMatter() {
    for (name in listOf("Bin", "BIN", "scripts", "SCRIPTS")) {
      assertThat(mayContain(name)).describedAs("a directory that holds '$name'").isTrue()
    }
  }

  /**
   * The name of a binary follows the pattern of the layout, and the pattern of posix reads the case. A file
   * named `Python3` therefore reaches no interpreter through `findInterpreter` either, so the filter may
   * reject it and stays a superset.
   */
  @Test
  fun testTheCaseOfABinaryNameFollowsTheLayout() {
    assertThat(mayContain("python3")).describedAs("the name of the posix layout").isTrue()
    assertThat(mayContain("Python3")).describedAs("the posix pattern reads the case").isFalse()
    assertThat(mayContain("PYTHON.EXE")).describedAs("the windows pattern ignores the case").isTrue()
  }

  /** The second way: the binary lies directly in the directory, as in the root of an installation. */
  @Test
  fun testABinaryInTheDirectoryPasses() {
    for (name in listOf("python", "python3", "python3.11", "pythonw", "pypy", "pypy3.10", "python3.13t",
                        "python.exe", "pythonw.exe", "python3.11.exe", "python_d.exe")) {
      assertThat(mayContain(name)).describedAs("a directory that holds '$name'").isTrue()
    }
  }

  @Test
  fun testAnOrdinaryDirectoryDoesNotPass() {
    assertThat(mayContain("src", "tests", "README.md", "pyproject.toml")).isFalse()
    assertThat(mayContain()).describedAs("no child at all").isFalse()
  }

  /**
   * The filter reads the patterns of the layout, so a name that only starts like a binary does not pass.
   * `findInterpreter` would reject the same name.
   */
  @Test
  fun testANameThatOnlyStartsLikeABinaryDoesNotPass() {
    assertThat(mayContain("python_helper")).isFalse()
    assertThat(mayContain("pythonrc")).isFalse()
    assertThat(mayContain("bins")).describedAs("`bins` is not `bin`").isFalse()
  }
}

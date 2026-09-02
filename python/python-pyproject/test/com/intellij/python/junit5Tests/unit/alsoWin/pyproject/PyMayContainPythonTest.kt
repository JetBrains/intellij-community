// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.model.internal.platformBridge.mayContainPython
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

  private fun mayContain(vararg childNames: String): Boolean = mayContainPython(childNames.asSequence())

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
  fun testTheCaseOfTheNameDoesNotMatter() {
    for (name in listOf("Bin", "BIN", "scripts", "SCRIPTS", "Python3", "PyPy")) {
      assertThat(mayContain(name)).describedAs("a directory that holds '$name'").isTrue()
    }
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
   * The filter is wider than the patterns of the method, because it tests a prefix. Such a name only costs
   * one call of the method, which then answers that the directory holds no interpreter.
   */
  @Test
  fun testAWiderNameStillPasses() {
    assertThat(mayContain("python_helper")).isTrue()
    assertThat(mayContain("pythonrc")).isTrue()
    assertThat(mayContain("bins")).describedAs("`bins` is not `bin`").isFalse()
  }
}

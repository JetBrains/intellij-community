// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.kindId
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The detector reads the environment off the file system layout, so each test writes a layout and reads the result back.
 *
 * Covers the kinds this module still owns, plus the extension point itself. The venv kind lives in
 * `intellij.python.venv`, and `VenvEnvironmentTest` covers it there.
 */
@TestApplication
internal class PythonEnvironmentDetectorTest {

  @Test
  fun condaVersion(@TempDir root: Path) {
    val binary = createConda(root, "python-3.14.4-h1234567_0.json", "numpy-2.3.4-py314h0_0.json")

    val conda = assertInstanceOf(PythonEnvironment.Conda::class.java, binary.detectPythonEnvironment().orThrow())

    assertEquals("3.14.4", conda.version)
    assertEquals(root.resolve("conda-meta"), conda.condaMetaPath)
    assertFalse(conda.isBase, "$root is not a base conda installation")
  }

  @Test
  fun condaWithoutPython(@TempDir root: Path) {
    val binary = createConda(root, "numpy-2.3.4-py314h0_0.json")

    val conda = assertInstanceOf(PythonEnvironment.Conda::class.java, binary.detectPythonEnvironment().orThrow())

    assertNull(conda.version)
  }

  /** A `condabin` or an `envs` directory marks the base conda installation. */
  @Test
  fun condaBase(@TempDir root: Path) {
    val binary = createConda(root, "python-3.14.4-h1234567_0.json")
    root.resolve("condabin").createDirectories()

    val conda = assertInstanceOf(PythonEnvironment.Conda::class.java, binary.detectPythonEnvironment().orThrow())

    assertTrue(conda.isBase, "$root is a base conda installation")
  }

  @Test
  fun systemPython(@TempDir root: Path) {
    val environment = createPythonBinary(root).detectPythonEnvironment().orThrow()

    assertInstanceOf(PythonEnvironment.SystemPython::class.java, environment)
    assertNull(environment.version)
  }

  /**
   * The registered ids, in order.
   *
   * The ids are a public contract, because the MCP `get_python_environment` tool reports them. They live in xml, so a
   * test guards them. `system` claims any layout and must stay last.
   */
  @Test
  fun providerIds() {
    assertEquals(listOf("venv", "conda", "system"), PythonEnvironmentProvider.EP_NAME.extensionList.map { it.id })
  }

  @Test
  fun kindIdNamesTheEnvironment(@TempDir root: Path) {
    assertAll(
      Executable {
        assertEquals("conda", createConda(root.resolve("conda"), "python-3.14.4-h0_0.json").detectPythonEnvironment().orThrow().kindId)
      },
      Executable {
        assertEquals("system", createPythonBinary(root.resolve("system")).detectPythonEnvironment().orThrow().kindId)
      },
    )
  }

  /** Writes a conda environment in [root]: one `conda-meta` entry per name in [packages], and the interpreter. */
  private fun createConda(root: Path, vararg packages: String): PythonBinary {
    val condaMeta = root.resolve("conda-meta").createDirectories()
    for (name in packages) {
      condaMeta.resolve(name).writeText("{}")
    }
    return createPythonBinary(root)
  }

  /**
   * Writes the interpreter that the detector starts from.
   *
   * The detector finds the home through the name of the parent directory, so the name must match the host.
   * Windows keeps the interpreter in `Scripts`, and Posix keeps it in `bin`.
   */
  private fun createPythonBinary(root: Path): PythonBinary {
    val binDir = root.resolve(if (SystemInfo.isWindows) "Scripts" else "bin").createDirectories()
    val binary = binDir.resolve(if (SystemInfo.isWindows) "python.exe" else "python")
    binary.writeText("")
    if (!SystemInfo.isWindows) {
      Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwxr-xr-x"))
    }
    return binary
  }
}

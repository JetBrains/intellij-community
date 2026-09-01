// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda.environment

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.kindId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * [CondaEnvironmentProvider] reads the environment off the file system layout, so each test writes a layout and reads
 * the result back.
 */
@TestApplication
internal class CondaEnvironmentTest {

  @Test
  fun condaVersion(@TempDir root: Path) {
    val binary = createConda(root, "python-3.14.4-h1234567_0.json", "numpy-2.3.4-py314h0_0.json")

    val conda = assertInstanceOf(CondaEnvironment::class.java, binary.detectPythonEnvironment().orThrow())

    assertEquals("3.14.4", conda.version)
    assertEquals(root.resolve("conda-meta"), conda.condaMetaPath)
    assertFalse(conda.isBase, "$root is not a base conda installation")
    // The `id` the xml declares. The MCP `get_python_environment` tool reports it.
    assertEquals("conda", conda.kindId)
  }

  @Test
  fun condaWithoutPython(@TempDir root: Path) {
    val binary = createConda(root, "numpy-2.3.4-py314h0_0.json")

    val conda = assertInstanceOf(CondaEnvironment::class.java, binary.detectPythonEnvironment().orThrow())

    assertNull(conda.version)
  }

  /** A `condabin` or an `envs` directory marks the base conda installation. */
  @Test
  fun condaBase(@TempDir root: Path) {
    val binary = createConda(root, "python-3.14.4-h1234567_0.json")
    root.resolve("condabin").createDirectories()

    val conda = assertInstanceOf(CondaEnvironment::class.java, binary.detectPythonEnvironment().orThrow())

    assertTrue(conda.isBase, "$root is a base conda installation")
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
   * The provider finds the home through the name of the parent directory, so the name must match the host.
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

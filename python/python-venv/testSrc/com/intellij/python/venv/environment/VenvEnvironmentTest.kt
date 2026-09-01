// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv.environment

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PythonBinary
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.kindId
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
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
 * [VenvEnvironmentProvider] reads the environment off the file system layout, so each test writes a layout and reads
 * the result back.
 *
 * No PEP defines a version key in `pyvenv.cfg`. Every tool writes its own key in its own shape, so the version cases
 * cover the standard library `venv`, `virtualenv`, uv and PEP 838.
 */
@TestApplication
internal class VenvEnvironmentTest {

  @Test
  fun venvVersion(@TempDir root: Path) {
    val cases = listOf(
      // The standard library `venv` writes `version` only.
      "version = 3.14.4" to "3.14.4",
      // `virtualenv` writes both keys. The `final` release level adds nothing to the version.
      "version_info = 3.14.4.final.0\nversion = 3.14.4" to "3.14.4",
      // A pre-release keeps its release level, in the short PEP 440 form.
      "version_info = 3.14.0.candidate.1\nversion = 3.14.0" to "3.14.0rc1",
      "version_info = 3.15.0.alpha.4" to "3.15.0a4",
      "version_info = 3.15.0.beta.2" to "3.15.0b2",
      // uv writes `version_info` without a release level.
      "version_info = 3.14.4" to "3.14.4",
      // PEP 838 writes the major and the minor version only.
      "python-version = 3.14" to "3.14",
      // The parser drops a release level that it does not know.
      "version_info = 3.14.0.gamma.1" to "3.14.0",
      // The parser drops a release level without a serial.
      "version_info = 3.14.0.candidate" to "3.14.0",
      // A value the parser does not accept falls through to the next key.
      "version_info = \nversion = 3.14.4" to "3.14.4",
      "version_info = unknown\nversion = 3.14.4" to "3.14.4",
      // No version key gives no version.
      "home = /usr/bin" to null,
    )
    assertAll(cases.mapIndexed { index, (config, expected) ->
      Executable {
        val environment = createVenv(root.resolve("venv$index"), config).detectPythonEnvironment().orThrow()
        assertEquals(expected, environment.version, config)
      }
    })
  }

  @Test
  fun venvLayout(@TempDir root: Path) {
    val config = "home = /usr/bin\ninclude-system-site-packages = false\nversion = 3.14.4"
    val binary = createVenv(root, config)

    val venv = assertInstanceOf(VenvEnvironment::class.java, binary.detectPythonEnvironment().orThrow())

    assertEquals(binary, venv.pythonBinaryPath)
    assertEquals(root, venv.pythonHomePath)
    assertEquals("3.14.4", venv.version)
    assertEquals(mapOf("home" to "/usr/bin", "include-system-site-packages" to "false", "version" to "3.14.4"), venv.config)
    assertTrue(venv.libRoot.startsWith(root.resolve("lib")), "${venv.libRoot} is outside the lib root")
    // The `id` the xml declares. The MCP `get_python_environment` tool reports it.
    assertEquals("venv", venv.kindId)
  }

  /** A Python 2.7-era `virtualenv` has no `pyvenv.cfg`. It marks the layout with `activate_this.py` and records no version. */
  @Test
  fun legacyVirtualenv(@TempDir root: Path) {
    val binary = createPythonBinary(root)
    binary.resolveSibling("activate_this.py").writeText("")
    root.resolve("lib").resolve("python2.7").createDirectories()

    val venv = assertInstanceOf(VenvEnvironment::class.java, binary.detectPythonEnvironment().orThrow())

    assertNull(venv.version)
    assertTrue(venv.config.isEmpty(), "${venv.config} is not empty")
  }

  /** Writes a virtual environment in [root]: the `pyvenv.cfg` with [config], the library root, and the interpreter. */
  private fun createVenv(root: Path, config: String): PythonBinary {
    root.createDirectories()
    root.resolve("pyvenv.cfg").writeText(config)
    root.resolve("lib").resolve("python3.14").createDirectories()
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

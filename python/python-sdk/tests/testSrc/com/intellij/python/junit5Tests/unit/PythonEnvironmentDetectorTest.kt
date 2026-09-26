// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PythonBinary
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.kindId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The detector reads the environment off the file system layout, so each test writes a layout and reads the result back.
 *
 * Covers the system kind, which this module owns, plus the extension point itself. Each other kind is covered by the
 * module that owns it: `VenvEnvironmentTest` and `CondaEnvironmentTest`.
 */
@TestApplication
internal class PythonEnvironmentDetectorTest {

  @Test
  fun systemPython(@TempDir root: Path) {
    val environment = createPythonBinary(root).detectPythonEnvironment().orThrow()

    assertInstanceOf(SystemPythonEnvironment::class.java, environment)
    assertNull(environment.version)
  }

  @Test
  fun kindIdNamesTheEnvironment(@TempDir root: Path) {
    assertEquals("system", createPythonBinary(root).detectPythonEnvironment().orThrow().kindId)
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

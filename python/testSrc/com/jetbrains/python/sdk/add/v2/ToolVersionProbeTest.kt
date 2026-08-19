// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.google.gson.JsonParser
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
class ToolVersionProbeTest {

  @Test
  @Timeout(30)
  @DisabledOnOs(OS.WINDOWS)
  fun `environment detection probes visible and hidden direct children`(@TempDir workingDirectory: Path): Unit = timeoutRunBlocking {
    createFakePython(workingDirectory.resolve("venv"), "Python 3.12.7", freeThreaded = false)
    createFakePython(workingDirectory.resolve(".venv"), "Python 3.13.1", freeThreaded = true)
    createFakePython(workingDirectory.resolve("space env"), "Python 3.11.9", freeThreaded = false)
    createBrokenPython(workingDirectory.resolve("broken"))
    workingDirectory.resolve("not-an-environment").createDirectories()

    val helper = requireNotNull(PythonHelpersLocator.findPathInHelpersPossibleNull("tool_version_probe.sh"))
    val output = ExecService().execGetStdout(
      BinOnEel(Path.of("/bin/sh"), workingDirectory),
      Args(helper.toString(), "--python", "", "--detect-environments", workingDirectory.toString()),
    ).getOrThrow()

    val environmentRecords = requireNotNull(JsonParser.parseString(output).asJsonObject.getAsJsonArray("environments"))
    val environments = environmentRecords.associateBy { it.asJsonObject.get("path").asString }
    val expectedPaths = setOf(
      workingDirectory.resolve("venv/bin/python").toString(),
      workingDirectory.resolve(".venv/bin/python").toString(),
      workingDirectory.resolve("space env/bin/python").toString(),
    )

    assertEquals(expectedPaths.size, environmentRecords.size())
    assertEquals(expectedPaths, environments.keys)
    assertEquals("Python 3.12.7", environments.getValue(workingDirectory.resolve("venv/bin/python").toString())
      .asJsonObject.getAsJsonObject("python").get("versionOutput").asString)
    assertEquals(true, environments.getValue(workingDirectory.resolve(".venv/bin/python").toString())
      .asJsonObject.getAsJsonObject("python").get("freeThreaded").asBoolean)
  }

  @Test
  @Timeout(30)
  @DisabledOnOs(OS.WINDOWS)
  fun `environment detection also probes a different process working directory`(@TempDir tempDirectory: Path): Unit = timeoutRunBlocking {
    val requestedWorkingDirectory = tempDirectory.resolve("requested").createDirectories()
    val processWorkingDirectory = tempDirectory.resolve("actual").createDirectories()
    createFakePython(requestedWorkingDirectory.resolve("requested-venv"), "Python 3.12.7", freeThreaded = false)
    createFakePython(processWorkingDirectory.resolve("actual-venv"), "Python 3.13.1", freeThreaded = true)

    val helper = requireNotNull(PythonHelpersLocator.findPathInHelpersPossibleNull("tool_version_probe.sh"))
    val output = ExecService().execGetStdout(
      BinOnEel(Path.of("/bin/sh"), processWorkingDirectory),
      Args(helper.toString(), "--python", "", "--detect-environments", requestedWorkingDirectory.toString()),
    ).getOrThrow()

    val environmentRecords = requireNotNull(JsonParser.parseString(output).asJsonObject.getAsJsonArray("environments"))
    val actualPaths = environmentRecords.mapTo(mutableSetOf()) { it.asJsonObject.get("path").asString }
    val expectedPaths = setOf(
      requestedWorkingDirectory.resolve("requested-venv/bin/python").toString(),
      processWorkingDirectory.toRealPath().resolve("actual-venv/bin/python").toString(),
    )

    assertEquals(expectedPaths, actualPaths)
  }

  private fun createFakePython(environmentRoot: Path, version: String, freeThreaded: Boolean) {
    val python = environmentRoot.resolve("bin/python")
    python.parent.createDirectories()
    python.writeText(
      $$"""
      |#!/bin/sh
      |if [ "$1" = "-c" ]; then
      |  printf '%s\n' '$$freeThreaded'
      |elif [ "$1" = "--version" ]; then
      |  printf '%s\n' '$$version'
      |else
      |  exit 1
      |fi
      """.trimMargin()
    )
    Files.setPosixFilePermissions(python, PosixFilePermissions.fromString("rwx------"))
  }

  private fun createBrokenPython(environmentRoot: Path) {
    val python = environmentRoot.resolve("bin/python")
    python.parent.createDirectories()
    python.writeText("#!/bin/sh\nexit 1\n")
    Files.setPosixFilePermissions(python, PosixFilePermissions.fromString("rwx------"))
  }
}

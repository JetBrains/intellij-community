// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.google.gson.JsonParser
import com.intellij.platform.eel.provider.asEelPath
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
import org.junit.jupiter.api.Assertions.assertNotNull
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
internal class ToolVersionProbeTest {

  @Test
  @Timeout(30)
  @DisabledOnOs(OS.WINDOWS)
  fun `parser reads the helper output for an executable Python`(@TempDir tempDirectory: Path): Unit = timeoutRunBlocking {
    val workingDirectory = tempDirectory.resolve("project").createDirectories()
    val python = createFakePython(tempDirectory.resolve("system"), "Python 3.12.7", freeThreaded = false)
    val environmentPython = createFakePython(workingDirectory.resolve("venv"), "Python 3.13.1", freeThreaded = true)
    val pythonPath = PathHolder.Target(python.toString())

    val output = runHelper(workingDirectory, python.toString(), workingDirectory.toString())
    val snapshot = parseTargetProbeOutput(output, emptyList(), pythonPath).getOrThrow()

    assertEquals(TargetPythonProbeResult(pythonPath, TargetPythonProbe.Executable(false, "Python 3.12.7")), snapshot.python)
    assertEquals(
      listOf(TargetEnvironmentProbe(PathHolder.Target(environmentPython.toString()), TargetPythonProbe.Executable(true, "Python 3.13.1"))),
      snapshot.environments,
    )
  }

  @Test
  @Timeout(30)
  @DisabledOnOs(OS.WINDOWS)
  fun `parser reads the helper output for a Python that does not run`(@TempDir tempDirectory: Path): Unit = timeoutRunBlocking {
    val python = createBrokenPython(tempDirectory.resolve("broken"))
    val pythonPath = PathHolder.Target(python.toString())

    val output = runHelper(tempDirectory, python.toString(), "")
    val snapshot = parseTargetProbeOutput(output, emptyList(), pythonPath).getOrThrow()

    assertEquals(TargetPythonProbeResult(pythonPath, TargetPythonProbe.NotExecutable), snapshot.python)
  }

  @Test
  fun `unknown Python status is invalid output`() {
    val output = """{"shell":"/bin/sh","home":"/home/user","python":{"status":"unknown"}}"""

    val result = parseTargetProbeOutput(output, emptyList(), PathHolder.Target("/usr/bin/python3"))

    assertNotNull(result.errorOrNull)
  }

  @Test
  fun `environment with a Python that does not run is invalid output`() {
    val output = """{"shell":"/bin/sh","home":"/home/user","environments":[{"path":"/venv/bin/python","python":{"status":"notExecutable"}}]}"""

    val result = parseTargetProbeOutput(output, emptyList(), null)

    assertNotNull(result.errorOrNull)
  }

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
      BinOnEel(Path.of("/bin/sh"), workingDirectory.asEelPath()),
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
      BinOnEel(Path.of("/bin/sh"), processWorkingDirectory.asEelPath()),
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

  private suspend fun runHelper(processWorkingDirectory: Path, pythonPath: String, detectEnvironmentsDirectory: String): String {
    val helper = requireNotNull(PythonHelpersLocator.findPathInHelpersPossibleNull("tool_version_probe.sh"))
    return ExecService().execGetStdout(
      BinOnEel(Path.of("/bin/sh"), processWorkingDirectory.asEelPath()),
      Args(helper.toString(), "--python", pythonPath, "--detect-environments", detectEnvironmentsDirectory),
    ).getOrThrow()
  }

  private fun createFakePython(environmentRoot: Path, version: String, freeThreaded: Boolean): Path {
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
    return python
  }

  private fun createBrokenPython(environmentRoot: Path): Path {
    val python = environmentRoot.resolve("bin/python")
    python.parent.createDirectories()
    python.writeText("#!/bin/sh\nexit 1\n")
    Files.setPosixFilePermissions(python, PosixFilePermissions.fromString("rwx------"))
    return python
  }
}

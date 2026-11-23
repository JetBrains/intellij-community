// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.execution.ExecutionException
import com.intellij.idea.TestFor
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyright.PyrightLspClientDescriptor
import com.intellij.python.pytools.backend.PyExecutable
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.isExecutable
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * The command line of the pyright-family language server.
 *
 * The CLI and the `-langserver` executable are separate entries of [PyTool.executables], and each one has its own
 * custom path. The server runs the custom path of the `-langserver` entry with `--stdio`, and never the custom path
 * of the CLI.
 */
@TestApplication
@TestFor(classes = [PyrightLspClientDescriptor::class], issues = ["PY-93978"])
@Subsystems.LspTools
@Layers.Functional
internal class PyrightCommandLineTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture(projectPath)
  private val toolPath = tempPathFixture(prefix = "tool")

  @ParameterizedTest
  @ValueSource(strings = ["pyright", "basedpyright"])
  fun `the custom path of the language server starts with --stdio`(packageName: String) = timeoutRunBlocking(30.seconds) {
    val tool = enable(packageName)
    val cli = createExecutable(packageName)
    val languageServer = createExecutable("custom-$packageName-langserver")

    val commandLine = withCustomPath(tool, cli) {
      withCustomPath(languageServerOf(tool), languageServer) {
        PyrightLspClientDescriptor(pyProjectFixture.get()).resolveCommandLine()
      }
    }

    assertEquals(languageServer, Path.of(commandLine.exePath))
    assertEquals(listOf("--stdio"), commandLine.parametersList.list)
  }

  @ParameterizedTest
  @ValueSource(strings = ["pyright", "basedpyright"])
  fun `the custom path of the CLI does not start the language server`(packageName: String) = timeoutRunBlocking(30.seconds) {
    val tool = enable(packageName)
    val cli = createExecutable(packageName)
    val sibling = createExecutable("$packageName-langserver")

    // Without the custom path of the language server, the lookup goes on to `PATH` and `uvx`. A test machine can
    // have neither of them, so the lookup can also fail.
    val commandLine = withCustomPath(tool, cli) {
      try {
        PyrightLspClientDescriptor(pyProjectFixture.get()).resolveCommandLine()
      }
      catch (_: ExecutionException) {
        null
      }
    }

    val exePath = commandLine?.exePath?.let(Path::of)
    assertNotEquals(cli, exePath)
    assertNotEquals(sibling, exePath, "A file beside the CLI is no custom path of the language server")
  }

  /** Selects the tool of [packageName] for the project. The state is set directly, so that no language server starts. */
  private fun enable(packageName: String): PyTool {
    val tool = checkNotNull(PyTool.findByPackageName(packageName))
    PyToolsState.getInstance(projectFixture.get()).setEnabled(tool, true)
    return tool
  }

  private fun languageServerOf(tool: PyTool): PyExecutable =
    tool.executables.single { it.fusId.value == "${tool.packageName.name}-langserver" }

  private fun createExecutable(name: String): Path {
    val path = toolPath.get().resolve("bin").resolve(name)
    path.parent.createDirectories()
    path.writeText("")
    if (!SystemInfoRt.isWindows) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
    }
    assertTrue(path.isExecutable()) { "Cannot make $path executable" }
    return path
  }

  /** Runs [action] with [path] as the custom path of [executable], then restores the old one. The store is application-wide. */
  private suspend fun <T> withCustomPath(executable: PyExecutable, path: Path, action: suspend () -> T): T {
    val eelDescriptor = projectFixture.get().getEelDescriptor()
    val previous = executable.getCustomExecutablePath(eelDescriptor)
    executable.setCustomExecutablePath(eelDescriptor, path)
    try {
      return action()
    }
    finally {
      executable.setCustomExecutablePath(eelDescriptor, previous)
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellFileInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
internal class ShellCdCommandTest(private val engine: TerminalEngine) {
  @JvmField
  @Rule
  val projectRule: ProjectRule = ProjectRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun engine(): List<TerminalEngine> = TerminalTestUtil.enginesWithCompletionSupport()
  }

  private val separator = File.separatorChar
  private val expectedDirectories = listOf("directory$separator", "settings$separator", ".hiddenDir$separator")
  private val allFiles = expectedDirectories + listOf("file.txt", ".hidden")

  @Test
  fun `suggest directories and hardcoded suggestions`() = runBlocking {
    val fixture = createFixture(allFiles, expectedPath = ".")
    val actual = fixture.getCompletions("cd ")
    assertSameElements(actual.map { it.name }, expectedDirectories + listOf("-", "~"))
  }

  @Test
  fun `suggest only directories if there is base path`() = runBlocking {
    val fixture = createFixture(allFiles, expectedPath = "src$separator")
    val actual = fixture.getCompletions("cd src$separator")
    assertSameElements(actual.map { it.name }, expectedDirectories)
  }

  /**
   * @param expectedPath path used for requesting the child files.
   * @param files files to return on [expectedPath] child files request.
   */
  private fun createFixture(files: List<String>, expectedPath: String): ShellCompletionTestFixture {
    return ShellCompletionTestFixture.builder(projectRule.project)
      .setIsReworkedTerminal(engine == TerminalEngine.REWORKED)
      .mockShellCommandResults { command ->
        if (command == "${GET_DIRECTORY_FILES.functionName} $expectedPath") {
          ShellCommandResult.create(files.joinToString("\n"), exitCode = 0)
        }
        else error("Unknown command: $command")
      }
      .mockFileSystemSupport(object : ShellFileSystemSupport {
        override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
          return files.map { it.toShellFileInfo(separator) }
        }
      })
      .build()
  }
}

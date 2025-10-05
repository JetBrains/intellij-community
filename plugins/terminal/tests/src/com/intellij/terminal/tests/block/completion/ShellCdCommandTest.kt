// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
internal class ShellCdCommandTest : BasePlatformTestCase() {
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
    return ShellCompletionTestFixture.builder(project)
      .mockShellCommandResults { command ->
        if (command == "${GET_DIRECTORY_FILES.functionName} $expectedPath") {
          ShellCommandResult.create(files.joinToString("\n"), exitCode = 0)
        }
        else error("Unknown command: $command")
      }
      .build()
  }
}

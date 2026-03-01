// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.completion.spec.specs.make.ShellMakeCommandSpec
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Language("makefile")
private const val MAKEFILE: String = """
foo: ## Foo comment
${"\t"}@run foo > /dev/null

bar: foo
${"\t"}@run bar > /dev/null

MODULE ?= "foo" # Just a variable

xyz: bar nonexistent ######    Xyz comment 
${"\t"}@run xyz > /dev/null

# PHONY should be ignored
.PHONY: foo bar nonexistent

# Commented out target should be ignored
#ignored: foo bar ## Ignored comment

"""

private const val INVALID_MAKEFILE: String = """
This is an incorrect Makefile
"""

@RunWith(Parameterized::class)
class ShellMakefileCompletionTest(private val engine: TerminalEngine) : BasePlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun engine(): List<TerminalEngine> = listOf(TerminalEngine.REWORKED, TerminalEngine.NEW_TERMINAL)
  }

  private val commandName = "make"

  private val spec = ShellMakeCommandSpec.create()

  @Test
  fun `complete make`() {
    val suggestions = getMakefileSuggestions(MAKEFILE)
    val actual = suggestions.map { it.name to it.description }
    assertSameElements(actual, listOf(
      "foo" to "Foo comment",
      "bar" to "Dependencies: foo",
      "xyz" to "Xyz comment\nDependencies: bar nonexistent",
    ))
  }

  @Test
  fun `try complete on incorrect makefile`() {
    val suggestions = getMakefileSuggestions(INVALID_MAKEFILE)
    assertEmpty(suggestions)
  }

  private fun getMakefileSuggestions(makefile: String): List<ShellCompletionSuggestion> {
    val fixture = ShellCompletionTestFixture.builder(project)
      .setIsReworkedTerminal(engine == TerminalEngine.REWORKED)
      .mockCommandSpecs(spec)
      .mockShellCommandResults { command ->
        if (command.startsWith("command cat ") || command.startsWith("cat ")) {
          ShellCommandResult.create(makefile, 0)
        }
        else {
          throw UnsupportedOperationException("Unsupported test command: $command")
        }
      }
      .build()

    return runBlocking {
      fixture.getCompletions("$commandName ")
    }
  }
}

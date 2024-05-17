// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.terminal.block.completion.spec.specs.make.ShellMakeCommandSpec
import org.jetbrains.plugins.terminal.block.util.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.block.util.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.block.util.TestRuntimeContextProvider
import org.junit.Test
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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

@RunWith(JUnit4::class)
class ShellMakefileCompletionTest {
  private val commandName = "make"

  private val spec = ShellMakeCommandSpec.create()

  @Test
  fun `complete make`() {
    val suggestions = getMakefileSuggestions(MAKEFILE, "")
    val actual = suggestions.map { it.name to it.description }
    assertSameElements(actual, listOf(
      "foo" to "Foo comment",
      "bar" to "Dependencies: foo",
      "xyz" to "Xyz comment\nDependencies: bar nonexistent",
    ))
  }

  @Test
  fun `try complete on incorrect makefile`() {
    val suggestions = getMakefileSuggestions(INVALID_MAKEFILE, "")
    assertEmpty(suggestions)
  }

  private fun getMakefileSuggestions(
    makefile: String,
    typedPrefix: String,
  ): List<ShellCompletionSuggestion> {
    val completion = createCompletion(ShellCommandExecutor { command ->
      if (command.startsWith("command cat ") || command.startsWith("cat ")) {
        return@ShellCommandExecutor ShellCommandResult.create(makefile, 0)
      }
      throw UnsupportedOperationException("Unsupported test command: $command")
    })

    val suggestions = runBlocking {
      completion.computeCompletionItems(commandName, listOf(typedPrefix))
    }
    return suggestions ?: fail("Completion suggestions are null")
  }

  private fun createCompletion(shellCommandExecutor: ShellCommandExecutor): ShellCommandSpecCompletion {

    val completion = ShellCommandSpecCompletion(
      TestCommandSpecsManager(spec),
      TestGeneratorsExecutor(),
      TestRuntimeContextProvider(directory = "/foo/tmp/", generatorCommandsRunner = shellCommandExecutor)
    )
    return completion
  }

}

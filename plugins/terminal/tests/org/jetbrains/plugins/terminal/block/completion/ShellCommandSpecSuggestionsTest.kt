// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.terminal.block.completion.ShellCommandSpecCompletion
import com.intellij.terminal.block.completion.spec.ShellCommandParserDirectives
import com.intellij.terminal.block.completion.spec.ShellCommandResult
import com.intellij.testFramework.UsefulTestCase.assertNotNull
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.block.util.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.block.util.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.block.util.TestRuntimeContextProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ShellCommandSpecSuggestionsTest {
  private val commandName = "command"
  private var filePathSuggestions: List<String> = emptyList()

  private val spec = ShellCommandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde") {
      isPersistent = true
      repeatTimes = 2
    }
    option("--argum") {
      argument {
        isOptional = true
        suggestions("all", "none", "default")
      }
    }

    argument {
      isOptional = true
      suggestions("abc")
    }

    subcommands {
      subcommand("sub") {
        option("-o", "--opt1")
        option("-a") {
          repeatTimes = 0
        }
        option("--long")
        option("--withReqArg") {
          argument {
            suggestions("argValue")
          }
        }
        option("--withOptArg") {
          argument {
            isOptional = true
          }
        }

        argument {
          suggestions("file")
        }
        argument {
          isOptional = true
          suggestions("s1")
        }
      }

      subcommand("excl") {
        option("-a") {
          exclusiveOn = listOf("-b")
        }
        option("-b") {
          exclusiveOn = listOf("-a")
        }
        option("-c")
        option("-d") {
          dependsOn = listOf("-a", "-c")
        }
      }

      subcommand("reqSub") {
        requiresSubcommand = true
        subcommands {
          subcommand("abc")
        }
        option("-a")
      }

      subcommand("manyArgs") {
        argument {
          isOptional = true
          suggestions("arg1")
        }
        argument {
          suggestions("arg2", "arg22")
        }
        argument {
          isOptional = true
          suggestions("arg3")
        }
        argument {
          suggestions("arg4", "arg44")
        }
      }

      subcommand("optPrecedeArgs") {
        parserDirectives = ShellCommandParserDirectives.create(optionsMustPrecedeArguments = true)
        option("-c")
        option("-d")
        argument {
          isOptional = true
          suggestions("arg")
        }

        subcommands {
          subcommand("sub") {
            option("-e")
            option("-f")
            argument {
              suggestions("arg2")
            }
          }
        }
      }

      subcommand("variadic") {
        option("-a")
        option("--var") {
          argument {
            isVariadic = true
            suggestions("var1", "var2")
          }
        }
        argument {
          suggestions("req")
        }
        argument {
          isVariadic = true
          suggestions("v")
        }
        argument {
          isOptional = true
          suggestions("opt")
        }
      }

      subcommand("variadic2") {
        option("-b")
        option("---") {
          argument {
            isVariadic = true
            optionsCanBreakVariadicArg = false
            suggestions("var")
          }
        }
        argument {
          isVariadic = true
          optionsCanBreakVariadicArg = false
          suggestions("v")
        }
        argument {
          suggestions("end")
        }
      }

      subcommand("cdWithSuggestions") {
        argument {
          suggestions("-", "~")
          suggestions(fileSuggestionsGenerator(onlyDirectories = true))
        }
      }

      subcommand("cd") {
        argument {
          suggestions(fileSuggestionsGenerator(onlyDirectories = true))
        }
      }
    }
  }

  @Test
  fun `main command`() {
    doTest(expected = listOf("sub", "excl", "reqSub", "manyArgs", "optPrecedeArgs", "variadic", "variadic2", "cdWithSuggestions", "cd",
                             "-a", "--asd", "--bcde", "--argum", "abc"))
  }

  @Test
  fun `suggest arguments and other options for option`() {
    doTest("--argum", expected = listOf("all", "none", "default", "-a", "--asd", "--bcde", "abc"))
  }

  @Test
  fun `suggest persistent option for subcommand`() {
    doTest("sub", expected = listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file"))
  }

  @Test
  fun `suggest twice repeating option for the second time`() {
    doTest("--bcde", expected = listOf("-a", "--asd", "--bcde", "--argum", "abc"))
  }

  @Test
  fun `do not suggest twice repeating option for the third time`() {
    doTest("--bcde", "--bcde", expected = listOf("-a", "--asd", "--argum", "abc"))
  }

  @Test
  fun `suggest infinitely repeating option again`() {
    doTest("sub", "-a", "-a", "-a", expected = listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file"))
  }

  @Test
  fun `do not suggest excluded option`() {
    doTest("excl", "-a", expected = listOf("-c", "--bcde"))
  }

  @Test
  fun `suggest option only if dependants present`() {
    doTest("excl", "-a", "-c", expected = listOf("-d", "--bcde"))
  }

  @Test
  fun `do not suggest options if command requires subcommand`() {
    doTest("reqSub", expected = listOf("abc"))
  }

  @Test
  fun `do not suggest next options if current option have required argument`() {
    doTest("sub", "--withReqArg", expected = listOf("argValue"))
  }

  @Test
  fun `suggest arguments till first required arg (no existing args)`() {
    doTest("manyArgs", expected = listOf("--bcde", "arg1", "arg2", "arg22"))
  }

  @Test
  fun `suggest arguments till first required arg (with existing args)`() {
    doTest("manyArgs", "arg22", expected = listOf("--bcde", "arg3", "arg4", "arg44"))
  }

  @Test
  fun `suggest variadic argument of option again`() {
    doTest("variadic", "--var", "var1", "var2", expected = listOf("var1", "var2", "-a", "--bcde", "req"))
  }

  @Test
  fun `suggest variadic argument of command again`() {
    doTest("variadic", "req", "v", expected = listOf("v", "opt", "-a", "--var", "--bcde"))
  }

  @Test
  fun `do not suggest variadic arg again after other arg`() {
    doTest("variadic", "req", "v", "opt", expected = listOf("-a", "--var", "--bcde"))
  }

  @Test
  fun `suggest options after argument`() {
    doTest("sub", "-a", "file", expected = listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "s1", "--bcde"))
  }

  @Test
  fun `do not suggest options after argument if it is restricted`() {
    doTest("optPrecedeArgs", "-c", "arg", expected = listOf())
  }

  @Test
  fun `do not suggest options after argument if it is restricted (parser directive is propagated from parent command)`() {
    doTest("optPrecedeArgs", "sub", "-f", "arg2", expected = listOf())
  }

  @Test
  fun `suggest variadic arg of command and options after breaking variadic arg with option`() {
    doTest("variadic", "req", "v", "-a", expected = listOf("--var", "--bcde", "v", "opt"))
  }

  @Test
  fun `do not suggest options after variadic arg of command if it is restricted`() {
    doTest("variadic2", "v", "v", expected = listOf("v", "end"))
  }

  @Test
  fun `do not suggest options after variadic arg of option if it is restricted`() {
    doTest("variadic2", "---", "var", expected = listOf("var"))
  }

  @Test
  fun `suggest hardcoded suggestions with files`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    doTest("cdWithSuggestions", expected = listOf("dir$separator", "folder$separator", "-", "~", "--bcde"))
  }

  @Test
  fun `suggest filenames for path in quotes`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    doTest("cd", typedPrefix = "\"someDir$separator", expected = listOf("dir$separator", "folder$separator"))
  }

  private fun doTest(vararg arguments: String, typedPrefix: String = "", expected: List<String>) = runBlocking {
    // Mock fileSuggestionsGenerator result
    val generatorCommandsRunner = object : ShellGeneratorCommandsRunner {
      override suspend fun runGeneratorCommand(command: String): ShellCommandResult {
        val output = filePathSuggestions.joinToString("\n")
        return ShellCommandResult.create(output, exitCode = 0)
      }
    }
    val completion = ShellCommandSpecCompletion(
      TestCommandSpecsManager(spec),
      TestGeneratorsExecutor(),
      TestRuntimeContextProvider(generatorCommandsRunner = generatorCommandsRunner)
    )
    val suggestions = completion.computeCompletionItems(commandName, arguments.toList() + typedPrefix)
    assertNotNull("Completion suggestions are null", suggestions)
    val actual = suggestions!!.flatMap { it.names }
    assertSameElements(actual, expected)
  }

  private fun mockFilePathsSuggestions(vararg files: String) {
    filePathSuggestions = files.asList()
  }
}
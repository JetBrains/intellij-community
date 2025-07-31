// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestRuntimeContextProvider
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
internal class ShellCommandSpecSuggestionsTest {
  private val commandName = "command"

  /**
   * The list of names to be returned by files list generator.
   *
   * Long story short: Use to mock `ls`.
   */
  private var filePathSuggestions: List<String> = emptyList()

  @Before
  fun setUp() {
    filePathSuggestions = emptyList()
  }

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
        parserOptions = ShellCommandParserOptions.create(optionsMustPrecedeArguments = true)
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

      subcommand("withTwoOptArgs") {
        option("--opt") {
          argument {
            suggestions("3", "4", "5")
            isOptional = true
          }
        }

        argument {
          suggestions("1", "2", "3")
          isOptional = true
        }
        argument {
          suggestions("2", "3", "4")
          isOptional = true
        }
      }

      subcommand("withDynamicOptions") {
        dynamicOptions {
          option("-a")
          option("-b")
        }
        option("-b")
        option("-c")
      }

      subcommand("multipleDynamicOptionsCalls") {
        dynamicOptions { option("-a") }
        dynamicOptions { option("-b") }
        option("-c")
      }

      subcommand("withMultipleSubcommandsCalls") {
        subcommands { subcommand("sub1") }
        subcommands { subcommand("sub2") }
      }
    }
  }

  @Test
  fun `main command`() {
    assertSameElements(getSuggestions(emptyList()), listOf("sub", "excl", "reqSub", "manyArgs", "optPrecedeArgs", "variadic", "variadic2", "cdWithSuggestions", "cd",
                                                           "withTwoOptArgs", "withDynamicOptions", "multipleDynamicOptionsCalls", "withMultipleSubcommandsCalls",
                                                           "-a", "--asd", "--bcde", "--argum", "abc"))
  }

  @Test
  fun `suggest arguments and other options for option`() {
    assertSameElements(getSuggestions(listOf("--argum")), listOf("all", "none", "default", "-a", "--asd", "--bcde", "abc"))
  }

  @Test
  fun `suggest persistent option for subcommand`() {
    assertSameElements(getSuggestions(listOf("sub")), listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file"))
  }

  @Test
  fun `suggest twice repeating option for the second time`() {
    assertSameElements(getSuggestions(listOf("--bcde")), listOf("-a", "--asd", "--bcde", "--argum", "abc"))
  }

  @Test
  fun `do not suggest twice repeating option for the third time`() {
    assertSameElements(getSuggestions(listOf("--bcde", "--bcde")), listOf("-a", "--asd", "--argum", "abc"))
  }

  @Test
  fun `suggest infinitely repeating option again`() {
    assertSameElements(getSuggestions(listOf("sub", "-a", "-a", "-a")), listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file"))
  }

  @Test
  fun `do not suggest excluded option`() {
    assertSameElements(getSuggestions(listOf("excl", "-a")), listOf("-c", "--bcde"))
  }

  @Test
  fun `suggest option only if dependants present`() {
    assertSameElements(getSuggestions(listOf("excl", "-a", "-c")), listOf("-d", "--bcde"))
  }

  @Test
  fun `do not suggest options if command requires subcommand`() {
    assertSameElements(getSuggestions(listOf("reqSub")), listOf("abc"))
  }

  @Test
  fun `do not suggest next options if current option have required argument`() {
    assertSameElements(getSuggestions(listOf("sub", "--withReqArg")), listOf("argValue"))
  }

  @Test
  fun `suggest arguments till first required arg (no existing args)`() {
    assertSameElements(getSuggestions(listOf("manyArgs")), listOf("--bcde", "arg1", "arg2", "arg22"))
  }

  @Test
  fun `suggest arguments till first required arg (with existing args)`() {
    assertSameElements(getSuggestions(listOf("manyArgs", "arg22")), listOf("--bcde", "arg3", "arg4", "arg44"))
  }

  @Test
  fun `suggest variadic argument of option again`() {
    assertSameElements(getSuggestions(listOf("variadic", "--var", "var1", "var2")), listOf("var1", "var2", "-a", "--bcde", "req"))
  }

  @Test
  fun `suggest variadic argument of command again`() {
    assertSameElements(getSuggestions(listOf("variadic", "req", "v")), listOf("v", "opt", "-a", "--var", "--bcde"))
  }

  @Test
  fun `do not suggest variadic arg again after other arg`() {
    assertSameElements(getSuggestions(listOf("variadic", "req", "v", "opt")), listOf("-a", "--var", "--bcde"))
  }

  @Test
  fun `suggest options after argument`() {
    assertSameElements(getSuggestions(listOf("sub", "-a", "file")), listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "s1", "--bcde"))
  }

  @Test
  fun `do not suggest options after argument if it is restricted`() {
    assertSameElements(getSuggestions(listOf("optPrecedeArgs", "-c", "arg")), listOf())
  }

  @Test
  fun `do not suggest options after argument if it is restricted (parser directive is propagated from parent command)`() {
    assertSameElements(getSuggestions(listOf("optPrecedeArgs", "sub", "-f", "arg2")), listOf())
  }

  @Test
  fun `suggest variadic arg of command and options after breaking variadic arg with option`() {
    assertSameElements(getSuggestions(listOf("variadic", "req", "v", "-a")), listOf("--var", "--bcde", "v", "opt"))
  }

  @Test
  fun `do not suggest options after variadic arg of command if it is restricted`() {
    assertSameElements(getSuggestions(listOf("variadic2", "v", "v")), listOf("v", "end"))
  }

  @Test
  fun `do not suggest options after variadic arg of option if it is restricted`() {
    assertSameElements(getSuggestions(listOf("variadic2", "---", "var")), listOf("var"))
  }

  @Test
  fun `suggest hardcoded suggestions with files`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(getSuggestions(listOf("cdWithSuggestions")), listOf("dir$separator", "folder$separator", "-", "~", "--bcde"))
  }

  @Test
  fun `suggest filenames for path in single quotes`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(getSuggestions(listOf("cd"), "'someDir$separator"), listOf("dir$separator", "folder$separator"))
  }

  @Test
  fun `suggest filenames for path in double quotes`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(getSuggestions(listOf("cd"), "\"someDir$separator"), listOf("dir$separator", "folder$separator"))
  }

  @Test
  fun `do not duplicate suggestions for command arguments`() {
    assertSameElements(getSuggestions(listOf("withTwoOptArgs")), listOf("1", "2", "3", "4", "--opt", "--bcde"))
  }

  @Test
  fun `do not duplicate suggestions for option arguments and command arguments`() {
    assertSameElements(getSuggestions(listOf("withTwoOptArgs", "--opt")), listOf("1", "2", "3", "4", "5", "--bcde"))
  }

  /** It also tests that if any option is declared as static and dynamic, it won't be suggested twice */
  @Test
  fun `suggest both static and dynamic options`() {
    assertSameElements(getSuggestions(listOf("withDynamicOptions")), listOf("-a", "-b", "-c", "--bcde"))
  }

  @Test
  fun `suggest dynamic options if they are defined in separate 'dynamicOptions' calls`() {
    assertSameElements(getSuggestions(listOf("multipleDynamicOptionsCalls")), listOf("-a", "-b", "-c", "--bcde"))
  }

  @Test
  fun `suggest subcommands if they are defined in separate 'subcommands' calls`() {
    assertSameElements(getSuggestions(listOf("withMultipleSubcommandsCalls")), listOf("sub1", "sub2", "--bcde"))
  }

  private fun getSuggestions(arguments: List<String>): List<String> = getSuggestions(arguments, "")

  private fun getSuggestions(
    arguments: List<String>,
    incompleteToken: String,
  ): List<String> {
    val completion = createCompletion(filePathSuggestions)
    return runBlocking {
      completion.computeCompletionItems(commandName, arguments + incompleteToken)
        ?.map { it.name }
      ?: fail { "Completion suggestions are null" }
    }
  }

  private fun createCompletion(
    mockFiles: List<String> = emptyList(),
  ): ShellCommandSpecCompletion {
    // Mock fileSuggestionsGenerator result
    val generatorCommandsRunner = ShellCommandExecutor {
      val output = mockFiles.joinToString("\n")
      ShellCommandResult.create(output, exitCode = 0)
    }
    val completion = ShellCommandSpecCompletion(
      TestCommandSpecsManager(spec),
      TestGeneratorsExecutor(),
      TestRuntimeContextProvider(generatorCommandsRunner = generatorCommandsRunner)
    )
    return completion
  }

  private fun mockFilePathsSuggestions(vararg files: String) {
    filePathSuggestions = files.asList()
  }

}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellFileInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestRuntimeContextProvider
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
internal class ShellCommandSpecSuggestionsTest(private val engine: TerminalEngine) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun engine(): List<TerminalEngine> = TerminalTestUtil.enginesWithCompletionSupport()
  }

  private val commandName = "command"

  /**
   * The list of names to be returned by files list generator.
   *
   * Long story short: Use to mock `ls`.
   */
  private var filePathSuggestions: List<String> = emptyList()
  private val separator = File.separatorChar

  @Before
  fun setUp() {
    filePathSuggestions = emptyList()
  }

  private val spec = ShellCommandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde") {
      persistent()
      repeatTimes(2)
    }
    option("--argum") {
      argument {
        optional()
        suggestions("all", "none", "default")
      }
    }

    argument {
      optional()
      suggestions("abc")
    }

    subcommands {
      subcommand("sub") {
        option("-o", "--opt1")
        option("-a") {
          repeatTimes(0)
        }
        option("--long")
        option("--withReqArg") {
          argument {
            suggestions("argValue")
          }
        }
        option("--withOptArg") {
          argument {
            optional()
          }
        }

        argument {
          suggestions("file")
        }
        argument {
          optional()
          suggestions("s1")
        }
      }

      subcommand("excl") {
        option("-a") {
          exclusiveOn(listOf("-b"))
        }
        option("-b") {
          exclusiveOn(listOf("-a"))
        }
        option("-c")
        option("-d") {
          dependsOn(listOf("-a", "-c"))
        }
      }

      subcommand("reqSub") {
        requiresSubcommand()
        subcommands {
          subcommand("abc")
        }
        option("-a")
      }

      subcommand("manyArgs") {
        argument {
          optional()
          suggestions("arg1")
        }
        argument {
          suggestions("arg2", "arg22")
        }
        argument {
          optional()
          suggestions("arg3")
        }
        argument {
          suggestions("arg4", "arg44")
        }
      }

      subcommand("optPrecedeArgs") {
        parserOptions(
          ShellCommandParserOptions.builder()
            .optionsMustPrecedeArguments(true)
            .build()
        )
        option("-c")
        option("-d")
        argument {
          optional()
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
            variadic()
            suggestions("var1", "var2")
          }
        }
        argument {
          suggestions("req")
        }
        argument {
          variadic()
          suggestions("v")
        }
        argument {
          optional()
          suggestions("opt")
        }
      }

      subcommand("variadic2") {
        option("-b")
        option("---") {
          argument {
            variadic()
            optionsCantBreakVariadicArg()
            suggestions("var")
          }
        }
        argument {
          variadic()
          optionsCantBreakVariadicArg()
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
            optional()
          }
        }

        argument {
          suggestions("1", "2", "3")
          optional()
        }
        argument {
          suggestions("2", "3", "4")
          optional()
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
    assertSameElements(
      getSuggestions(arguments = emptyList()),
      listOf(
        "sub",
        "excl",
        "reqSub",
        "manyArgs",
        "optPrecedeArgs",
        "variadic",
        "variadic2",
        "cdWithSuggestions",
        "cd",
        "withTwoOptArgs",
        "withDynamicOptions",
        "multipleDynamicOptionsCalls",
        "withMultipleSubcommandsCalls",
        "-a",
        "--asd",
        "--bcde",
        "--argum",
        "abc"
      )
    )
  }

  @Test
  fun `suggest arguments and other options for option`() {
    assertSameElements(
      getSuggestions(arguments = listOf("--argum")),
      listOf("all", "none", "default", "-a", "--asd", "--bcde", "abc")
    )
  }

  @Test
  fun `suggest persistent option for subcommand`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub")),
      listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file")
    )
  }

  @Test
  fun `suggest twice repeating option for the second time`() {
    assertSameElements(
      getSuggestions(arguments = listOf("--bcde")),
      listOf("-a", "--asd", "--bcde", "--argum", "abc")
    )
  }

  @Test
  fun `do not suggest twice repeating option for the third time`() {
    assertSameElements(
      getSuggestions(arguments = listOf("--bcde", "--bcde")),
      listOf("-a", "--asd", "--argum", "abc")
    )
  }

  @Test
  fun `suggest infinitely repeating option again`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub", "-a", "-a", "-a")),
      listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file")
    )
  }

  @Test
  fun `do not suggest excluded option`() {
    assertSameElements(
      getSuggestions(arguments = listOf("excl", "-a")),
      listOf("-c", "--bcde")
    )
  }

  @Test
  fun `suggest option only if dependants present`() {
    assertSameElements(
      getSuggestions(arguments = listOf("excl", "-a", "-c")),
      listOf("-d", "--bcde")
    )
  }

  @Test
  fun `do not suggest options if command requires subcommand`() {
    assertSameElements(
      getSuggestions(arguments = listOf("reqSub")),
      listOf("abc")
    )
  }

  @Test
  fun `do not suggest next options if current option have required argument`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub", "--withReqArg")),
      listOf("argValue")
    )
  }

  @Test
  fun `suggest arguments till first required arg (no existing args)`() {
    assertSameElements(
      getSuggestions(arguments = listOf("manyArgs")),
      listOf("--bcde", "arg1", "arg2", "arg22")
    )
  }

  @Test
  fun `suggest arguments till first required arg (with existing args)`() {
    assertSameElements(
      getSuggestions(arguments = listOf("manyArgs", "arg22")),
      listOf("--bcde", "arg3", "arg4", "arg44")
    )
  }

  @Test
  fun `suggest variadic argument of option again`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic", "--var", "var1", "var2")),
      listOf("var1", "var2", "-a", "--bcde", "req")
    )
  }

  @Test
  fun `suggest variadic argument of command again`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic", "req", "v")),
      listOf("v", "opt", "-a", "--var", "--bcde")
    )
  }

  @Test
  fun `do not suggest variadic arg again after other arg`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic", "req", "v", "opt")),
      listOf("-a", "--var", "--bcde")
    )
  }

  @Test
  fun `suggest options after argument`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub", "-a", "file")),
      listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "s1", "--bcde")
    )
  }

  @Test
  fun `do not suggest options after argument if it is restricted`() {
    assertSameElements(
      getSuggestions(arguments = listOf("optPrecedeArgs", "-c", "arg")),
      listOf()
    )
  }

  @Test
  fun `do not suggest options after argument if it is restricted (parser directive is propagated from parent command)`() {
    assertSameElements(
      getSuggestions(arguments = listOf("optPrecedeArgs", "sub", "-f", "arg2")),
      listOf()
    )
  }

  @Test
  fun `suggest variadic arg of command and options after breaking variadic arg with option`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic", "req", "v", "-a")),
      listOf("--var", "--bcde", "v", "opt")
    )
  }

  @Test
  fun `do not suggest options after variadic arg of command if it is restricted`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic2", "v", "v")),
      listOf("v", "end")
    )
  }

  @Test
  fun `do not suggest options after variadic arg of option if it is restricted`() {
    assertSameElements(
      getSuggestions(arguments = listOf("variadic2", "---", "var")),
      listOf("var")
    )
  }

  @Test
  fun `suggest hardcoded suggestions with files`() {
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(
      getSuggestions(arguments = listOf("cdWithSuggestions")),
      listOf("dir$separator", "folder$separator", "-", "~", "--bcde")
    )
  }

  @Test
  fun `suggest subcommands after quote`() {
    assertSameElements(
      getSuggestions(arguments = listOf("reqSub"), incompleteToken = "'"),
      listOf("abc")
    )
  }

  @Test
  fun `suggest options after quote`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub"), incompleteToken = "'"),
      listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file")
    )
  }

  @Test
  fun `suggest filenames and options after quote`() {
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(
      getSuggestions(arguments = listOf("cdWithSuggestions"), incompleteToken = "'"),
      listOf("dir$separator", "folder$separator", "-", "~", "--bcde")
    )
  }

  @Test
  fun `suggest filenames for path in single quotes`() {
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(
      getSuggestions(arguments = listOf("cd"), incompleteToken = "'someDir$separator"),
      listOf("dir$separator", "folder$separator")
    )
  }

  @Test
  fun `suggest filenames for path in double quotes`() {
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    assertSameElements(
      getSuggestions(arguments = listOf("cd"), incompleteToken = "\"someDir$separator"),
      listOf("dir$separator", "folder$separator")
    )
  }

  @Test
  fun `do not duplicate suggestions for command arguments`() {
    assertSameElements(
      getSuggestions(arguments = listOf("withTwoOptArgs")),
      listOf("1", "2", "3", "4", "--opt", "--bcde")
    )
  }

  @Test
  fun `do not duplicate suggestions for option arguments and command arguments`() {
    assertSameElements(
      getSuggestions(arguments = listOf("withTwoOptArgs", "--opt")),
      listOf("1", "2", "3", "4", "5", "--bcde")
    )
  }

  /** It also tests that if any option is declared as static and dynamic, it won't be suggested twice */
  @Test
  fun `suggest both static and dynamic options`() {
    assertSameElements(
      getSuggestions(arguments = listOf("withDynamicOptions")),
      listOf("-a", "-b", "-c", "--bcde")
    )
  }

  @Test
  fun `suggest dynamic options if they are defined in separate 'dynamicOptions' calls`() {
    assertSameElements(
      getSuggestions(arguments = listOf("multipleDynamicOptionsCalls")),
      listOf("-a", "-b", "-c", "--bcde")
    )
  }

  @Test
  fun `suggest subcommands if they are defined in separate 'subcommands' calls`() {
    assertSameElements(
      getSuggestions(arguments = listOf("withMultipleSubcommandsCalls")),
      listOf("sub1", "sub2", "--bcde")
    )
  }

  @Test
  fun `suggest if command is an absolute path (unix)`() {
    assertSameElements(
      getSuggestions(command = "/usr/bin/$commandName", arguments = listOf("manyArgs")),
      listOf("--bcde", "arg1", "arg2", "arg22")
    )
  }

  @Test
  fun `suggest if command is an absolute path (windows)`() {
    assertSameElements(
      getSuggestions(command = "C:\\Users\\User\\Programs\\$commandName", arguments = listOf("manyArgs")),
      listOf("--bcde", "arg1", "arg2", "arg22")
    )
  }

  @Test
  fun `suggest if command is a relative path (unix)`() {
    assertSameElements(
      getSuggestions(command = "./$commandName", arguments = listOf("manyArgs")),
      listOf("--bcde", "arg1", "arg2", "arg22")
    )
  }

  @Test
  fun `suggest if command is a relative path (windows)`() {
    assertSameElements(
      getSuggestions(command = ".\\$commandName", arguments = listOf("manyArgs")),
      listOf("--bcde", "arg1", "arg2", "arg22")
    )
  }

  @Test
  fun `suggest for subcommand after unknown token`() {
    assertSameElements(
      getSuggestions(arguments = listOf("sub", "unknown")),
      listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "file")
    )
  }

  private fun getSuggestions(
    command: String = commandName,
    arguments: List<String>,
  ): List<String> {
    return getSuggestions(command, arguments, "")
  }

  private fun getSuggestions(
    command: String = commandName,
    arguments: List<String>,
    incompleteToken: String,
  ): List<String> {
    val completion = createCompletion(filePathSuggestions)
    return runBlocking {
      completion.computeCompletionItems(command, arguments + incompleteToken)
        ?.map { it.name }
      ?: fail { "Completion suggestions are null" }
    }
  }

  private fun createCompletion(
    mockFiles: List<String> = emptyList(),
  ): ShellCommandSpecCompletion {
    // Mock fileSuggestionsGenerator result
    val generatorCommandsRunner = object : ShellCommandExecutor {
      override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult {
        val output = mockFiles.joinToString("\n")
        return ShellCommandResult.create(output, exitCode = 0)
      }
    }
    val fileSystemSupport = object : ShellFileSystemSupport {
      override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
        return mockFiles.map { it.toShellFileInfo(separator) }
      }
    }
    val runtimeContextProvider = TestRuntimeContextProvider(
      isReworkedTerminal = engine == TerminalEngine.REWORKED,
      generatorCommandsRunner = generatorCommandsRunner,
      fileSystemSupport = fileSystemSupport
    )
    val completion = ShellCommandSpecCompletion(
      TestCommandSpecsManager(spec),
      TestGeneratorsExecutor(),
      runtimeContextProvider,
    )
    return completion
  }

  private fun mockFilePathsSuggestions(vararg files: String) {
    filePathSuggestions = files.asList()
  }

}

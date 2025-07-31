// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion

import com.intellij.terminal.completion.ShellCommandTreeAssertions
import com.intellij.terminal.completion.ShellCommandTreeBuilderFixture
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.spec.ShellAliasSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestRuntimeContextProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
internal class ShellCommandTreeBuilderTest {
  private val commandName = "command"
  private var filePathSuggestions: Map<String, List<String>> = emptyMap()

  private val spec = ShellCommandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde")
    option("--argum") {
      argument {
        isOptional = true
        suggestions("someArg")
      }
    }

    argument {
      isOptional = true
      suggestions("aaa", "bbb")
    }

    subcommands {
      subcommand("with-alias") {
        subcommands {
          subcommand("al") {
            option("--manyArgs") {
              argument()
              argument {
                isOptional = true
                suggestions("a2")
              }
            }
          }
        }

        option("-a")

        argument {
          isOptional = true
          suggestions {
            listOf(
              ShellAliasSuggestion("alias-1", "al --manyArgs somearg"),
              ShellAliasSuggestion("alias-2", "-a"),
              ShellAliasSuggestion("recurse-1", "recurse-1"),
              ShellAliasSuggestion("recurse-2", "-a recurse-2"),
            )
          }
        }
      }

      subcommand("sub") {
        option("-o", "--opt1")
        option("-a")
        option("-b")
        option("--long")
        option("--withReqArg") {
          argument()
        }
        option("--withOptArg") {
          argument {
            isOptional = true
          }
        }
        option("--manyArgs") {
          argument()
          argument {
            isOptional = true
            suggestions("a2")
          }
        }
        option("--skippedArg") {
          argument {
            isOptional = true
          }
          argument()
          argument {
            isOptional = true
            suggestions("opt2")
          }
        }

        argument {
          suggestions("somePath")
        }
        argument {
          isOptional = true
          suggestions("arg1", "arg2")
        }
      }

      subcommand("nonPosix") {
        parserOptions = ShellCommandParserOptions.create(flagsArePosixNonCompliant = true)
        option("-a")
        option("-b")
      }

      subcommand("sep") {
        option("--withSeparator") {
          separator = "="
          argument()
        }
      }

      subcommand("withFiles") {
        option("-o") {
          argument {
            suggestions(fileSuggestionsGenerator())
          }
        }
        argument {
          suggestions(fileSuggestionsGenerator(onlyDirectories = true))
        }
      }

      subcommand("withTwoDirArgs") {
        argument {
          suggestions(fileSuggestionsGenerator(onlyDirectories = true))
        }
        argument {
          suggestions(fileSuggestionsGenerator(onlyDirectories = true))
        }
      }
    }
  }

  @Test
  fun `main command with options`() {
    doTest("--bcde", "-a") {
      assertOptionOf("--bcde", commandName)
      assertOptionOf("-a", commandName)
    }
  }

  @Test
  fun `main command with options and argument of command`() {
    doTest("--asd", "--bcde", "aaa") {
      assertOptionOf("--asd", commandName)
      assertOptionOf("--bcde", commandName)
      assertArgumentOfSubcommand("aaa", commandName)
    }
  }

  // argument is assigned to option first if it is possible
  @Test
  fun `main command with options and argument of option`() {
    doTest("-a", "--argum", "someArg") {
      assertOptionOf("-a", commandName)
      assertOptionOf("--argum", commandName)
      assertArgumentOfOption("someArg", "--argum")
    }
  }

  @Test
  fun `main command with argument and option with argument sequentially`() {
    doTest("--argum", "someArg", "bbb") {
      assertOptionOf("--argum", commandName)
      assertArgumentOfOption("someArg", "--argum")
      assertArgumentOfSubcommand("bbb", commandName)
    }
  }

  @Test
  fun `main command with argument and option with argument divided by option`() {
    doTest("--argum", "someArg", "--asd", "aaa") {
      assertOptionOf("--argum", commandName)
      assertArgumentOfOption("someArg", "--argum")
      assertOptionOf("--asd", commandName)
      assertArgumentOfSubcommand("aaa", commandName)
    }
  }

  @Test
  fun `subcommand with options and argument`() {
    doTest("sub", "-o", "-b", "somePath") {
      assertSubcommandOf("sub", commandName)
      assertOptionOf("-o", "sub")
      assertOptionOf("-b", "sub")
      assertArgumentOfSubcommand("somePath", "sub")
    }
  }

  @Test
  fun `subcommand with two arguments`() {
    doTest("sub", "somePath", "arg1") {
      assertSubcommandOf("sub", commandName)
      assertArgumentOfSubcommand("somePath", "sub")
      assertArgumentOfSubcommand("arg1", "sub")
    }
  }

  @Test
  fun `option with unknown arg`() {
    doTest("sub", "--manyArgs", "unknown", "a2") {
      assertSubcommandOf("sub", commandName)
      assertOptionOf("--manyArgs", "sub")
      assertArgumentOfOption("unknown", "--manyArgs")
      assertArgumentOfOption("a2", "--manyArgs")
    }
  }

  @Test
  fun `option with unknown arg preceded with skipped optional arg`() {
    doTest("sub", "--skippedArg", "unknown", "opt2") {
      assertSubcommandOf("sub", commandName)
      assertOptionOf("--skippedArg", "sub")
      assertArgumentOfOption("unknown", "--skippedArg")
      assertArgumentOfOption("opt2", "--skippedArg")
    }
  }

  @Test
  fun `chained options parsed as separate options`() {
    doTest("sub", "-aob") {
      assertSubcommandOf("sub", commandName)
      assertOptionOf("-a", "sub")
      assertOptionOf("-o", "sub")
      assertOptionOf("-b", "sub")
    }
  }

  @Test
  fun `chained options are not parsed as separate options if command is posix non compliant`() {
    doTest("nonPosix", "-ab") {
      assertSubcommandOf("nonPosix", commandName)
      assertUnknown("-ab", "nonPosix")
    }
  }

  @Test
  fun `option and argument with separator`() {
    doTest("sep", "--withSeparator=someArg") {
      assertSubcommandOf("sep", commandName)
      assertOptionOf("--withSeparator", "sep")
      assertArgumentOfOption("someArg", "--withSeparator")
    }
  }

  @Test
  fun `option with file argument`() {
    mockFilePathsSuggestions("." to listOf("file.txt", "folder${File.separatorChar}", "file"))
    doTest("withFiles", "-o", "file.txt") {
      assertSubcommandOf("withFiles", commandName)
      assertOptionOf("-o", "withFiles")
      assertArgumentOfOption("file.txt", "-o")
    }
  }

  @Test
  fun `option with file argument prefixed with directory name`() {
    val dir = "someDir${File.separatorChar}"
    mockFilePathsSuggestions(dir to listOf("file.txt", "folder${File.separatorChar}", "file"))
    doTest("withFiles", "-o", "${dir}file") {
      assertSubcommandOf("withFiles", commandName)
      assertOptionOf("-o", "withFiles")
      assertArgumentOfOption("${dir}file", "-o")
    }
  }

  @Test
  fun `subcommand with directory argument`() {
    val dirSuggestion = "dir${File.separatorChar}"
    mockFilePathsSuggestions(dirSuggestion to listOf("file.txt", "folder${File.separatorChar}", "file"))
    doTest("withFiles", dirSuggestion) {
      assertSubcommandOf("withFiles", commandName)
      assertArgumentOfSubcommand(dirSuggestion, "withFiles")
    }
  }

  @Test
  fun `subcommand with directory without ending slash`() {
    val dir = "someDir${File.separatorChar}"
    mockFilePathsSuggestions(dir to listOf("file.txt", "folder${File.separatorChar}", "file"))
    doTest("withFiles", "${dir}folder") {
      assertSubcommandOf("withFiles", commandName)
      assertArgumentOfSubcommand("${dir}folder", "withFiles")
    }
  }

  @Test
  fun `subcommand with two directory arguments`() {
    val separator = File.separatorChar
    val someDir = "someDir$separator"
    val otherDir = "otherDir$separator"
    val nestedDir = "nestedDir$separator"
    mockFilePathsSuggestions("." to listOf(someDir, otherDir),
                             someDir to listOf("file.txt", "file", nestedDir))
    doTest("withTwoDirArgs", "${someDir}$nestedDir", otherDir) {
      assertSubcommandOf("withTwoDirArgs", commandName)
      assertArgumentOfSubcommand("${someDir}$nestedDir", "withTwoDirArgs")
      assertArgumentOfSubcommand(otherDir, "withTwoDirArgs")
    }
  }

  @Test
  fun `recursive alias 1 isn't expanding infinitely`() {
    doTestWithTimeout("with-alias", "recurse-1") {
    }
  }

  @Test
  fun `recursive alias 2 isn't expanding infinitely`() {
    doTestWithTimeout("with-alias", "recurse-2") {
    }
  }

  @Test
  fun `subcommand alias resolves`() {
    doTest("with-alias", "alias-1") {
      assertSubcommandOf("with-alias", commandName)
      assertSubcommandOf("al", "with-alias")
      assertOptionOf("--manyArgs", "al")
      assertArgumentOfOption("somearg", "--manyArgs")
    }
  }

  @Test
  fun `subcommand alias resolves arguments`() {
    doTest("with-alias", "alias-1", "a2") {
      assertSubcommandOf("with-alias", commandName)
      assertSubcommandOf("al", "with-alias")
      assertOptionOf("--manyArgs", "al")
      assertArgumentOfOption("somearg", "--manyArgs")
      assertArgumentOfOption("a2", "--manyArgs")
    }
  }

  @Test
  fun `option alias resolves`() {
    doTest("with-alias", "alias-2") {
      assertSubcommandOf("with-alias", commandName)
      assertOptionOf("-a", "with-alias")
    }
  }

  private fun doTest(vararg arguments: String, assertions: ShellCommandTreeAssertions.() -> Unit) =
    runBlocking {
      doTestImpl(arguments.toList(), assertions)
    }

  private fun doTestWithTimeout(vararg arguments: String, timeout: Duration = 500.milliseconds, assertions: ShellCommandTreeAssertions.() -> Unit) =
    timeoutRunBlocking(timeout) {
      doTestImpl(arguments.toList(), assertions)
    }

  private suspend fun doTestImpl(arguments: List<String>, assertions: ShellCommandTreeAssertions.() -> Unit) {
    // Mock fileSuggestionsGenerator result
    val generatorCommandsRunner = ShellCommandExecutor { command ->
      if (command.startsWith(GET_DIRECTORY_FILES.functionName)) {
        val path = command.removePrefix(GET_DIRECTORY_FILES.functionName).trim()
      val files = filePathSuggestions[path]
        if (files != null) {
          ShellCommandResult.create(files.joinToString("\n"), exitCode = 0)
        }
      else ShellCommandResult.create("", exitCode = 0)
      }
      else ShellCommandResult.create("", exitCode = 1)
    }
    val fixture = ShellCommandTreeBuilderFixture(
      TestCommandSpecsManager(spec),
      TestGeneratorsExecutor(),
      TestRuntimeContextProvider(generatorCommandsRunner = generatorCommandsRunner)
    )
    fixture.buildCommandTreeAndTest(spec, arguments.toList(), assertions)
  }

  private fun mockFilePathsSuggestions(vararg pathToFiles: Pair<String, List<String>>) {
    filePathSuggestions = pathToFiles.toMap()
  }
}

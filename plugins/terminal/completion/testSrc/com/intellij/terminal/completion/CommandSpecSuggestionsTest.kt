// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import com.intellij.terminal.completion.util.FakeCommandSpecManager
import com.intellij.terminal.completion.util.FakeShellRuntimeDataProvider
import com.intellij.terminal.completion.util.commandSpec
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.util.containers.TreeTraversal
import kotlinx.coroutines.runBlocking
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellCommandParserDirectives
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class CommandSpecSuggestionsTest {
  private val commandName = "command"
  private var filePathSuggestions: List<String> = emptyList()
  private var shellEnvironment: ShellEnvironment? = null
  private var commandMap: Map<String, ShellCommand> = emptyMap()

  private val spec = commandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde") {
      isPersistent = true
      repeatTimes = 2
    }
    option("--argum") {
      argument("optArg", isOptional = true) {
        suggestions("all", "none", "default")
      }
    }

    argument("mainCmdArg", isOptional = true) {
      suggestions("abc")
    }

    subcommand("sub") {
      option("-o", "--opt1")
      option("-a") {
        repeatTimes = 0
      }
      option("--long")
      option("--withReqArg") {
        argument("reqArg") {
          suggestions("argValue")
        }
      }
      option("--withOptArg") {
        argument("optArg", isOptional = true)
      }

      argument("file") {
        suggestions("file")
      }
      argument("someOptArg", isOptional = true) {
        suggestions("s1")
      }
    }

    subcommand("excl") {
      option("-a") {
        exclusiveOn("-b")
      }
      option("-b") {
        exclusiveOn("-a")
      }
      option("-c")
      option("-d") {
        dependsOn("-a", "-c")
      }
    }

    subcommand("reqSub") {
      requiresSubcommand = true
      subcommand("abc")
      option("-a")
    }

    subcommand("manyArgs") {
      argument("a1", isOptional = true) {
        suggestions("arg1")
      }
      argument("a2") {
        suggestions("arg2", "arg22")
      }
      argument("a3", isOptional = true) {
        suggestions("arg3")
      }
      argument("a4") {
        suggestions("arg4", "arg44")
      }
    }

    subcommand("optPrecedeArgs") {
      parserDirectives = ShellCommandParserDirectives(optionsMustPrecedeArguments = true)
      option("-c")
      option("-d")
      argument("arg", isOptional = true) {
        suggestions("arg")
      }

      subcommand("sub") {
        option("-e")
        option("-f")
        argument("arg") {
          suggestions("arg2")
        }
      }
    }

    subcommand("variadic") {
      option("-a")
      option("--var") {
        argument("var") {
          isVariadic = true
          suggestions("var1", "var2")
        }
      }
      argument("req") {
        suggestions("req")
      }
      argument("var") {
        isVariadic = true
        suggestions("v")
      }
      argument("opt", isOptional = true) {
        suggestions("opt")
      }
    }

    subcommand("variadic2") {
      option("-b")
      option("---") {
        argument("varOpt") {
          isVariadic = true
          optionsCanBreakVariadicArg = false
          suggestions("var")
        }
      }
      argument("var") {
        isVariadic = true
        optionsCanBreakVariadicArg = false
        suggestions("v")
      }
      argument("end") {
        suggestions("end")
      }
    }

    subcommand("cd") {
      argument("dir") {
        templates("folders")
        suggestions("-", "~")
      }
    }

    subcommand("sudo") {
      argument("cmd") {
        isCommand = true
      }
    }
  }

  @Test
  fun `main command`() {
    doTest(expected = listOf("sub", "excl", "reqSub", "manyArgs", "optPrecedeArgs", "variadic", "variadic2", "cd", "sudo",
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
    doTest("cd", expected = listOf("dir$separator", "folder$separator", "-", "~", "--bcde"))
  }

  @Test
  fun `do not suggest hardcoded suggestions with files if some directory already typed`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    doTest("cd", typedPrefix = "someDir$separator", expected = listOf("dir$separator", "folder$separator"))
  }

  @Test
  fun `suggest filenames for path in quotes`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "dir$separator", "folder$separator")
    doTest("cd", typedPrefix = "\"someDir$separator", expected = listOf("dir$separator", "folder$separator"))
  }

  @Test
  fun `suggest command names for command argument`() {
    val commands = listOf("cmd", "ls", "git")
    mockShellEnvironment(ShellEnvironment(commands = commands))
    doTest("sudo", expected = commands + "--bcde")
  }

  @Test
  fun `suggest subcommands and options for nested command`() {
    val nestedCommandName = "cmd"
    val nestedCommand = commandSpec(nestedCommandName) {
      subcommand("sub")
      option("-a")
      option("--opt")
    }
    mockCommandManager(mapOf(nestedCommandName to nestedCommand))
    mockShellEnvironment(ShellEnvironment(commands = listOf(nestedCommandName)))
    doTest("sudo", nestedCommandName, expected = listOf("sub", "-a", "--opt"))
  }

  private fun doTest(vararg arguments: String, typedPrefix: String = "", expected: List<String>) = runBlocking {
    val commandSpecManager = FakeCommandSpecManager(commandMap)
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager,
                                                             FakeShellRuntimeDataProvider(filePathSuggestions, shellEnvironment))
    val rootNode: SubcommandNode = CommandTreeBuilder.build(suggestionsProvider, commandSpecManager,
                                                            commandName, spec, arguments.asList())
    val allChildren = TreeTraversal.PRE_ORDER_DFS.traversal(rootNode as CommandPartNode<*>) { node -> node.children }
    val lastNode = allChildren.last() ?: rootNode
    val actual = suggestionsProvider.getSuggestionsOfNext(lastNode, typedPrefix).flatMap { it.names }.filter { it.isNotEmpty() }

    assertSameElements(actual, expected)
  }

  private fun mockFilePathsSuggestions(vararg files: String) {
    filePathSuggestions = files.asList()
  }

  private fun mockShellEnvironment(env: ShellEnvironment) {
    shellEnvironment = env
  }

  private fun mockCommandManager(commands: Map<String, ShellCommand>) {
    commandMap = commands
  }
}
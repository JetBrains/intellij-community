// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import com.intellij.terminal.completion.util.FakeCommandSpecManager
import com.intellij.terminal.completion.util.FakeShellRuntimeDataProvider
import com.intellij.terminal.completion.util.commandSpec
import com.intellij.util.containers.JBIterable
import com.intellij.util.containers.TreeTraversal
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.terminal.completion.ShellCommandParserDirectives
import org.jetbrains.terminal.completion.ShellSuggestionsGenerator
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class CommandTreeBuilderTest {
  private val commandName = "command"
  private var filePathSuggestions: List<String> = emptyList()

  private val spec = commandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde")
    option("--argum") {
      argument("optArg", isOptional = true) {
        suggestions("someArg")
      }
    }

    argument("mainCmdArg", isOptional = true) {
      suggestions("aaa", "bbb")
    }

    subcommand("sub") {
      option("-o", "--opt1")
      option("-a")
      option("-b")
      option("--long")
      option("--withReqArg") {
        argument("reqArg")
      }
      option("--withOptArg") {
        argument("optArg", isOptional = true)
      }
      option("--manyArgs") {
        argument("a1")
        argument("a2", isOptional = true) {
          suggestions("a2")
        }
      }
      option("--skippedArg") {
        argument("opt", isOptional = true)
        argument("req")
        argument("opt2", isOptional = true) {
          suggestions("opt2")
        }
      }

      argument("file") {
        suggestions("somePath")
      }
      argument("someOptArg", isOptional = true) {
        suggestions("arg1", "arg2")
      }
    }

    subcommand("nonPosix") {
      parserDirectives = ShellCommandParserDirectives(flagsArePosixNoncompliant = true)
      option("-a")
      option("-b")
    }

    subcommand("sep") {
      option("--withSeparator") {
        separator = "="
        argument("arg")
      }
    }

    subcommand("withFiles") {
      option("-o") {
        argument("file") {
          templates("filepaths")
        }
      }
      argument("folder") {
        generator(ShellSuggestionsGenerator(templates = listOf("folders")))
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
    mockFilePathsSuggestions("file.txt", "folder/", "file")
    doTest("withFiles", "-o", "file.txt") {
      assertSubcommandOf("withFiles", commandName)
      assertOptionOf("-o", "withFiles")
      assertArgumentOfOption("file.txt", "-o")
    }
  }

  @Test
  fun `option with file argument prefixed with directory name`() {
    mockFilePathsSuggestions("file.txt", "folder/", "file")
    doTest("withFiles", "-o", "someDir/file") {
      assertSubcommandOf("withFiles", commandName)
      assertOptionOf("-o", "withFiles")
      assertArgumentOfOption("someDir/file", "-o")
    }
  }

  @Test
  fun `subcommand with directory argument`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "folder$separator", "dir$separator")
    doTest("withFiles", "dir$separator") {
      assertSubcommandOf("withFiles", commandName)
      assertArgumentOfSubcommand("dir$separator", "withFiles")
    }
  }

  @Test
  fun `subcommand with directory without ending slash`() {
    val separator = File.separatorChar
    mockFilePathsSuggestions("file.txt", "folder$separator", "dir$separator")
    doTest("withFiles", "someDir${separator}folder") {
      assertSubcommandOf("withFiles", commandName)
      assertArgumentOfSubcommand("someDir${separator}folder", "withFiles")
    }
  }

  private fun doTest(vararg arguments: String, assertions: CommandTreeAssertions.() -> Unit) = runBlocking {
    val commandSpecManager = FakeCommandSpecManager()
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, FakeShellRuntimeDataProvider(filePathSuggestions))
    val root = CommandTreeBuilder.build(suggestionsProvider, commandSpecManager,
                                        commandName, spec, arguments.asList())
    assertions(CommandTreeAssertions(root))
  }

  private fun mockFilePathsSuggestions(vararg files: String) {
    filePathSuggestions = files.asList()
  }

  private class CommandTreeAssertions(root: CommandPartNode<*>) {
    private val allChildren: JBIterable<CommandPartNode<*>> = TreeTraversal.PRE_ORDER_DFS.traversal(root) { node -> node.children }

    fun assertSubcommandOf(cmd: String, parentCmd: String) {
      val childNode = allChildren.find { it.text == cmd } ?: error("Not found node with name: $cmd")
      assertTrue("Expected that child is subcommand", childNode is SubcommandNode)
      assertTrue("Expected that parent of '$cmd' is a subcommand '$parentCmd', but was: ${childNode.parent}",
                 (childNode.parent as? SubcommandNode)?.text == parentCmd)
    }

    fun assertOptionOf(option: String, subcommand: String) {
      val childNode = allChildren.find { it.text == option } ?: error("Not found node with name: $option")
      assertTrue("Expected that child is option", childNode is OptionNode)
      assertTrue("Expected that parent of '$option' is a subcommand '$subcommand', but was: ${childNode.parent}",
                 (childNode.parent as? SubcommandNode)?.text == subcommand)
    }

    fun assertArgumentOfOption(arg: String, option: String) {
      val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
      assertTrue("Expected that child is argument", childNode is ArgumentNode)
      assertTrue("Expected that parent of '$arg' is an option '$option', but was: ${childNode.parent}",
                 (childNode.parent as? OptionNode)?.text == option)
    }

    fun assertArgumentOfSubcommand(arg: String, subcommand: String) {
      val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
      assertTrue("Expected that child is argument", childNode is ArgumentNode)
      assertTrue("Expected that parent of '$arg' is an option '$subcommand', but was: ${childNode.parent}",
                 (childNode.parent as? SubcommandNode)?.text == subcommand)
    }

    fun assertUnknown(child: String, parent: String) {
      val childNode = allChildren.find { it.text == child } ?: error("Not found node with name: $child")
      assertTrue("Expected that child is unknown", childNode is UnknownNode)
      assertTrue("Expected that parent of '$child' is '$parent', but was: ${childNode.parent}",
                 childNode.parent?.text == parent)
    }
  }
}
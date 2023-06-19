// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.util.containers.JBIterable
import com.intellij.util.containers.TreeTraversal
import junit.framework.TestCase.assertTrue
import org.jetbrains.plugins.terminal.exp.completion.*
import org.jetbrains.plugins.terminal.exp.util.commandSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CommandTreeBuilderTest {
  private val commandName = "command"

  private val spec = commandSpec(commandName) {
    option("-a", "--asd")
    option("--bcde")
    option("--argum") {
      argument("optArg", isOptional = true)
    }

    argument("mainCmdArg", isOptional = true)

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

      argument("file") {
        templates("filepath")
      }
      argument("someOptArg", isOptional = true)
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
    doTest("--asd", "--bcde", "someArg") {
      assertOptionOf("--asd", commandName)
      assertOptionOf("--bcde", commandName)
      assertArgumentOfSubcommand("someArg", commandName)
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
    doTest("--argum", "optArg", "cmdArg") {
      assertOptionOf("--argum", commandName)
      assertArgumentOfOption("optArg", "--argum")
      assertArgumentOfSubcommand("cmdArg", commandName)
    }
  }

  @Test
  fun `main command with argument and option with argument divided by option`() {
    doTest("--argum", "optArg", "--asd", "cmdArg") {
      assertOptionOf("--argum", commandName)
      assertArgumentOfOption("optArg", "--argum")
      assertOptionOf("--asd", commandName)
      assertArgumentOfSubcommand("cmdArg", commandName)
    }
  }

  @Test
  fun `subcommand with options and argument`() {
    doTest("sub", "-o", "-b", "someFilePath") {
      assertSubcommandOf("sub", commandName)
      assertOptionOf("-o", "sub")
      assertOptionOf("-b", "sub")
      assertArgumentOfSubcommand("someFilePath", "sub")
    }
  }

  @Test
  fun `subcommand with two arguments`() {
    doTest("sub", "arg1", "arg2") {
      assertSubcommandOf("sub", commandName)
      assertArgumentOfSubcommand("arg1", "sub")
      assertArgumentOfSubcommand("arg2", "sub")
    }
  }

  private fun doTest(vararg arguments: String, assertions: CommandTreeAssertions.() -> Unit) {
    val root = CommandTreeBuilder(commandName, spec, arguments.asList()).build()
    assertions(CommandTreeAssertions(root))
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
  }
}
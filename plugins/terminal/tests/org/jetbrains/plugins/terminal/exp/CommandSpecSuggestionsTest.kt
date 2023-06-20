// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.jetbrains.plugins.terminal.exp.util.commandSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CommandSpecSuggestionsTest {
  private val provider: CommandSpecCompletionProvider = CommandSpecCompletionProvider()
  private val commandName = "command"

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
        argument("reqArg")
      }
      option("--withOptArg") {
        argument("optArg", isOptional = true)
      }

      argument("file") {
        templates("filepath")
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
  }

  @Test
  fun `main command`() {
    doTest(expected = listOf("sub", "excl", "reqSub", "-a", "--asd", "--bcde", "--argum", "abc"))
  }

  @Test
  fun `argument of option`() {
    doTest("--argum", expected = listOf("all", "none", "default", "-a", "--asd", "--bcde", "abc"))
  }

  @Test
  fun `suggest persistent option for subcommand`() {
    doTest("sub", expected = listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "s1"))
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
    doTest("sub", "-a", "-a", "-a", expected = listOf("-o", "--opt1", "-a", "--long", "--withReqArg", "--withOptArg", "--bcde", "s1"))
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

  private fun doTest(vararg arguments: String, expected: List<String>) {
    val allArgs = arguments.asList() + ""
    val actual = provider.computeCompletionElements(spec, commandName, allArgs).map { it.lookupString }
    assertSameElements(actual, expected)
  }
}
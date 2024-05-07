// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.block.completion.spec.ShellCommandResult
import com.intellij.terminal.block.completion.spec.ShellName
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.impl.IJShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.exp.completion.IJShellCommandSpecsManager
import org.jetbrains.plugins.terminal.exp.util.TestJsonCommandSpecsProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShellCommandSpecManagerTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, listOf(TestJsonCommandSpecsProvider()), testRootDisposable)
  }

  @Test
  fun `load command spec for subcommand from separate file`() = runBlocking {
    val commandSpecsManager = IJShellCommandSpecsManager.getInstance()

    val spec = commandSpecsManager.getCommandSpec("main")
    assertNotNull("Failed to load main command spec", spec)

    val context = createDummyRuntimeContext()  // in this case, real context is not required
    val subcommands = spec!!.subcommandsGenerator.generate(context)
    assertTrue("Subcommands are empty: $spec", subcommands.isNotEmpty())

    val subcommand = subcommands.first()
    assertEmpty("Subcommand options are not empty in a light spec: $subcommand", subcommand.options)

    val loadedSubcommand = commandSpecsManager.getFullCommandSpec(subcommand)
    assertTrue("The subcommand is not fully loaded: $loadedSubcommand", loadedSubcommand.options.isNotEmpty())
  }

  private fun createDummyRuntimeContext(): ShellRuntimeContext {
    return IJShellRuntimeContext("", "", "", ShellName("dummy"), DummyGeneratorCommandsRunner())
  }

  private class DummyGeneratorCommandsRunner : ShellGeneratorCommandsRunner {
    override suspend fun runGeneratorCommand(command: String): ShellCommandResult {
      return ShellCommandResult.create("", 0)
    }
  }
}
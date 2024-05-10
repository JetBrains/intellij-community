// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.block.completion.spec.ShellName
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.impl.IJShellRuntimeContext
import org.jetbrains.plugins.terminal.block.util.DummyGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.exp.completion.IJShellCommandSpecsManager
import org.jetbrains.plugins.terminal.exp.util.TestCommandSpecsProvider
import org.jetbrains.plugins.terminal.exp.util.TestJsonCommandSpecsProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShellCommandSpecManagerTest : BasePlatformTestCase() {
  private val commandName: String = "main"

  private val commandSpecsManager: IJShellCommandSpecsManager
    get() = IJShellCommandSpecsManager.getInstance()

  /** In this test, real context is not required */
  private val runtimeContext: ShellRuntimeContext = createDummyRuntimeContext()

  @Test
  fun `check that there are no subcommands in a light spec`() = runBlocking {
    mockCommandSpecProviders(TestJsonCommandSpecsProvider())

    val lightSpec = commandSpecsManager.getLightCommandSpec(commandName)
    assertNotNull("Failed to find $commandName command spec", lightSpec)

    val subcommands = lightSpec!!.subcommandsGenerator.generate(runtimeContext)
    assertEmpty("There should be no subcommands in a light spec", subcommands)
  }

  @Test
  fun `load command spec for subcommand from separate file`() = runBlocking {
    mockCommandSpecProviders(TestJsonCommandSpecsProvider())

    val spec = commandSpecsManager.getCommandSpec(commandName)
    assertNotNull("Failed to load $commandName command spec", spec)

    val subcommands = spec!!.subcommandsGenerator.generate(runtimeContext)
    assertTrue("Subcommands are empty: $spec", subcommands.isNotEmpty())

    val subcommand = subcommands.first()
    assertEmpty("Subcommand options are not empty in a light spec: $subcommand", subcommand.options)

    val loadedSubcommand = commandSpecsManager.getFullCommandSpec(subcommand)
    assertTrue("The subcommand is not fully loaded: $loadedSubcommand", loadedSubcommand.options.isNotEmpty())
  }

  @Test
  fun `replace default json-based spec with kotlin-based spec`() = runBlocking {
    val replacingSpec = ShellCommandSpec(commandName) {}
    val replacingSpecProvider = TestCommandSpecsProvider(ShellCommandSpecInfo.create(replacingSpec, ShellCommandSpecConflictStrategy.REPLACE))
    mockCommandSpecProviders(TestJsonCommandSpecsProvider(), replacingSpecProvider)

    val spec = commandSpecsManager.getCommandSpec(commandName)
    assertEquals("Json based spec is not replaced with kotlin-based", replacingSpec, spec)
  }

  private fun mockCommandSpecProviders(vararg newProviders: ShellCommandSpecsProvider) {
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, newProviders.toList(), testRootDisposable)
  }

  private fun createDummyRuntimeContext(): ShellRuntimeContext {
    return IJShellRuntimeContext("", "", ShellName("dummy"), DummyGeneratorCommandsRunner())
  }
}
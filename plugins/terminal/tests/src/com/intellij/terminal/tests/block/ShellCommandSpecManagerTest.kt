// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.tests.block.util.TestCommandSpecsProvider
import com.intellij.terminal.tests.block.util.TestJsonCommandSpecsProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellMergedCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorCommandsRunner
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class ShellCommandSpecManagerTest : BasePlatformTestCase() {
  private val commandName: String = "main"

  private val commandSpecsManager: ShellCommandSpecsManagerImpl
    get() = ShellCommandSpecsManagerImpl.getInstance()

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
    assertEmpty("Subcommand options are not empty in a light spec: $subcommand",
                subcommand.allOptionsGenerator.generate(runtimeContext))

    val loadedSubcommand = commandSpecsManager.getFullCommandSpec(subcommand)
    assertTrue("The subcommand is not fully loaded: $loadedSubcommand",
               loadedSubcommand.allOptionsGenerator.generate(runtimeContext).isNotEmpty())
  }

  @Test
  fun `replace default json-based spec with kotlin-based spec`() = runBlocking {
    val replacingSpec = ShellCommandSpec(commandName) {}
    val replacingSpecProvider = TestCommandSpecsProvider(ShellCommandSpecInfo.create(replacingSpec, ShellCommandSpecConflictStrategy.REPLACE))
    mockCommandSpecProviders(TestJsonCommandSpecsProvider(), replacingSpecProvider)

    val spec = commandSpecsManager.getCommandSpec(commandName)
    assertEquals("Json based spec is not replaced with kotlin-based", replacingSpec, spec)
  }

  @Test
  fun `override default spec`() = runBlocking {
    val defaultDesc = "default"
    val overriddenDesc = "overridden"
    val defaultSpec = ShellCommandSpec(commandName) {
      subcommands {
        subcommand("firstCmd") { description(defaultDesc) }
        subcommand("secondCmd") { description(defaultDesc) }
      }
      option("--firstOpt") { description(defaultDesc) }
      dynamicOptions {
        option("--secondOpt") { description(defaultDesc) }
      }
    }
    val overrideSpec = ShellCommandSpec(commandName) {
      subcommands {
        subcommand("secondCmd") {
          description(overriddenDesc)
          subcommands {
            subcommand("secondCmdNewSubCommand")
          }
          option("--secondCmdNewOpt")
        }
        subcommand("thirdCmd")
      }
      option("--secondOpt") { description(overriddenDesc) }
      dynamicOptions {
        option("--thirdOpt")
      }
      argument()
    }

    val specsProvider = TestCommandSpecsProvider(
      ShellCommandSpecInfo.create(defaultSpec, ShellCommandSpecConflictStrategy.DEFAULT),
      ShellCommandSpecInfo.create(overrideSpec, ShellCommandSpecConflictStrategy.OVERRIDE),
    )
    mockCommandSpecProviders(specsProvider)

    val spec = commandSpecsManager.getCommandSpec(commandName) ?: error("Failed to load $commandName command spec")
    val subcommands = spec.subcommandsGenerator.generate(runtimeContext)

    val firstCommand = subcommands.find { it.name == "firstCmd" } ?: error("Failed to find the first command: $subcommands")
    assertTrue("First command is not from default spec: $subcommands",
               firstCommand.description == defaultDesc)
    val secondCommand = subcommands.find { it.name == "secondCmd" } ?: error("Failed to find the second command: $subcommands")
    assertTrue("Second command is not overridden: $subcommands", secondCommand.description == overriddenDesc)
    assertTrue("No single subcommand in the second command: $secondCommand",
               secondCommand.subcommandsGenerator.generate(runtimeContext).size == 1)
    assertTrue("No single option in the second command: $secondCommand",
               secondCommand.allOptionsGenerator.generate(runtimeContext).size == 1)
    assertTrue("Third command is not added: $subcommands", subcommands.any { it.name == "thirdCmd" })

    val options = spec.allOptionsGenerator.generate(runtimeContext)
    val firstOption = options.find { it.name == "--firstOpt" } ?: error("Failed to find the first option: $spec")
    assertTrue("First option is not from default spec: $spec", firstOption.description == defaultDesc)
    val secondOption = options.find { it.name == "--secondOpt" } ?: error("Failed to find the second option: $spec")
    assertTrue("Second option is not overridden: $spec", secondOption.description == overriddenDesc)
    assertTrue("Third option is not added: $spec", options.any { it.name == "--thirdOpt" })

    assertTrue("No single argument: $spec", spec.arguments.size == 1)
  }

  @Test
  fun `override not fully loaded json-based spec`() = runBlocking {
    val overrideSpec = ShellCommandSpec(commandName) {
      subcommands {
        subcommand("sub") {
          option("--newOpt")
        }
      }
    }

    val overrideSpecProvider = TestCommandSpecsProvider(ShellCommandSpecInfo.create(overrideSpec, ShellCommandSpecConflictStrategy.OVERRIDE))
    mockCommandSpecProviders(TestJsonCommandSpecsProvider(), overrideSpecProvider)

    val spec = commandSpecsManager.getCommandSpec(commandName) ?: error("Failed to load $commandName command spec")
    val subcommands = spec.subcommandsGenerator.generate(runtimeContext)

    val sub = subcommands.find { it.name == "sub" } ?: error("Failed to find the subcommand: $subcommands")
    val fullSub = commandSpecsManager.getFullCommandSpec(sub)
    val options = fullSub.allOptionsGenerator.generate(runtimeContext)
    // if there is no option - then the base spec is not fully loaded
    assertTrue("No option from the base spec: $fullSub", options.any { it.name == "--someOpt" })
    assertTrue("No option from the override spec: $fullSub", options.any { it.name == "--newOpt" })
  }

  @Test
  fun `check that merged spec is not created for the single overriding spec`() = runBlocking {
    val overrideSpec = ShellCommandSpec(commandName) {}
    val provider = TestCommandSpecsProvider(ShellCommandSpecInfo.create(overrideSpec, ShellCommandSpecConflictStrategy.OVERRIDE))
    mockCommandSpecProviders(provider)

    val spec = commandSpecsManager.getCommandSpec(commandName) ?: error("Failed to load $commandName command spec")
    assertTrue("Merged command spec is created, while there is only one spec", spec !is ShellMergedCommandSpec)
  }

  private fun mockCommandSpecProviders(vararg newProviders: ShellCommandSpecsProvider) {
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, newProviders.toList(), testRootDisposable)
  }

  private fun createDummyRuntimeContext(): ShellRuntimeContext {
    return ShellRuntimeContextImpl("", "", ShellName("dummy"), TestGeneratorCommandsRunner.DUMMY)
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.exp.completion.CommandSpecsBean
import org.jetbrains.plugins.terminal.exp.completion.IJCommandSpecManager
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CommandSpecLoadingTest : BasePlatformTestCase() {
  private val commandName = "main"

  override fun setUp() {
    super.setUp()
    val commandSpecsBean = CommandSpecsBean().apply {
      path = "completionSpec/all_commands.json"
      pluginDesc = DefaultPluginDescriptor(PluginId.findId("org.jetbrains.plugins.terminal")!!, javaClass.classLoader)
    }
    ExtensionTestUtil.addExtensions(CommandSpecsBean.EP_NAME, listOf(commandSpecsBean), testRootDisposable)
  }

  @Test
  fun `load command spec for subcommand from separate file`() = runBlocking {
    val spec = IJCommandSpecManager.getInstance().getCommandSpec(commandName) ?: error("Not found command with name: '$commandName'")
    val subcommand = spec.subcommands.firstOrNull() ?: error("Not found subcommands in spec: $spec")
    val specReference = subcommand.loadSpec ?: error("'loadSpec' property is null in subcommand: $subcommand")
    val loadedSubcommand = IJCommandSpecManager.getInstance().getCommandSpec(specReference)
    assertNotNull(loadedSubcommand)
  }
}
package com.intellij.terminal.tests.block.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider

@ApiStatus.Internal
class TestCommandSpecsProvider(private vararg val specs: ShellCommandSpecInfo) : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return specs.toList()
  }
}
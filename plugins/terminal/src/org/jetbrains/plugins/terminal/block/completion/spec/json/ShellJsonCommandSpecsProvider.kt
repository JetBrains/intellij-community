// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonCommandSpecsUtil.loadAndParseJson
import org.jetbrains.terminal.completion.ShellCommand

internal abstract class ShellJsonCommandSpecsProvider : ShellCommandSpecsProvider {
  protected abstract val shortDescriptionsJsonPath: String
  abstract val commandSpecsPath: String

  final override fun getCommandSpecs(): List<ShellCommandSpec> {
    val shellCommands: List<ShellCommand> = loadAndParseJson(shortDescriptionsJsonPath, this::class.java.classLoader)
                                            ?: return emptyList()
    return shellCommands.map { ShellJsonBasedCommandSpec(it) }
  }
}
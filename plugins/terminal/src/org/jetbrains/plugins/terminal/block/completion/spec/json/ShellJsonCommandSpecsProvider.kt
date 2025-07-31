// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonCommandSpecsUtil.loadAndParseJson
import org.jetbrains.terminal.completion.ShellCommand

@ApiStatus.Internal
abstract class ShellJsonCommandSpecsProvider : ShellCommandSpecsProvider {
  protected abstract val shortDescriptionsJsonPath: String
  abstract val commandSpecsPath: String

  final override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    val shellCommands: List<ShellCommand> = loadAndParseJson(shortDescriptionsJsonPath, this::class.java.classLoader)
                                            ?: return emptyList()
    return shellCommands.flatMap { cmd ->
      cmd.names.map { name ->
        // Use default conflict strategy, because json-based specs are used as base for other specs.
        ShellCommandSpecInfo.create(ShellJsonBasedCommandSpec(name, cmd), ShellCommandSpecConflictStrategy.DEFAULT)
      }
    }
  }
}
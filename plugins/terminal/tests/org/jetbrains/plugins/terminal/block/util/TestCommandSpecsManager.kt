// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.spec.ShellCommandSpec

internal class TestCommandSpecsManager(vararg specs: ShellCommandSpec) : ShellCommandSpecsManager {
  constructor(specs: List<ShellCommandSpec>) : this(*specs.toTypedArray())

  private val specs: Map<String, ShellCommandSpec> = specs.associateBy { it.name }

  override suspend fun getCommandSpec(commandName: String): ShellCommandSpec? {
    return specs[commandName]
  }

  override suspend fun getFullCommandSpec(spec: ShellCommandSpec): ShellCommandSpec {
    return spec
  }
}

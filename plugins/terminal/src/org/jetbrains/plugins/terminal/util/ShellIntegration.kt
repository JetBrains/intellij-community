// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("DEPRECATION")

package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration

data class ShellIntegration(val shellType: ShellType, val commandBlocks: Boolean) {

  constructor(shellType: ShellType, commandBlockIntegration: CommandBlockIntegration?) :
    this(shellType, commandBlockIntegration != null)

  @Suppress("unused")
  @Deprecated("Use commandBlocks instead")
  val commandBlockIntegration: CommandBlockIntegration?
    get() = if (commandBlocks) CommandBlockIntegration() else null
}

enum class ShellType {
  ZSH, BASH, FISH, POWERSHELL;

  companion object {
    val KEY: Key<ShellType> = Key.create("ShellType")
  }
}

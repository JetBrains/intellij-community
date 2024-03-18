// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration

data class ShellIntegration(val shellType: ShellType, val commandBlockIntegration: CommandBlockIntegration?)

enum class ShellType {
  ZSH, BASH, FISH, POWERSHELL
}

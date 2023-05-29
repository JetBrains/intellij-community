// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.util.Key

data class ShellIntegration(val shellType: ShellType, val withCommandBlocks: Boolean)

enum class ShellType {
  ZSH, BASH, FISH, POWERSHELL
}

val SHELL_TYPE_KEY: Key<ShellType> = Key.create("SHELL_TYPE")

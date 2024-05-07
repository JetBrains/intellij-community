// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.extensions.ExtensionPointName

interface ShellCommandSpecsProvider {
  fun getCommandSpecs(): List<ShellCommandSpecInfo>

  companion object {
    internal val EP_NAME: ExtensionPointName<ShellCommandSpecsProvider> = ExtensionPointName.create("org.jetbrains.plugins.terminal.commandSpecsProvider")
  }
}
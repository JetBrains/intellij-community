// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.terminal.block.completion.spec.ShellCommandSpec

interface ShellCommandSpecsProvider {
  fun getCommandSpecs(): List<ShellCommandSpec>

  companion object {
    internal val EP_NAME: ExtensionPointName<ShellCommandSpecsProvider> = ExtensionPointName.create("org.jetbrains.plugins.terminal.commandSpecsProvider")
  }
}
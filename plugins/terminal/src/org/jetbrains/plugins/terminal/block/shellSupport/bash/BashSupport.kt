// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.shellSupport.bash

import org.jetbrains.plugins.terminal.block.shellSupport.BaseShSupport

internal class BashSupport : BaseShSupport() {
  override fun splitAliases(aliasesDefinition: String): List<String> {
    return aliasesDefinition.removePrefix("alias ").split("\nalias ")
  }
}
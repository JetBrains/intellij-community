// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh.zsh

import org.jetbrains.plugins.terminal.sh.BaseShSupport

class ZshSupport : BaseShSupport() {
  override fun splitAliases(aliasesDefinition: String): List<String> {
    return aliasesDefinition.split("\n")
  }
}
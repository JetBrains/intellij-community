// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.util.Key
import com.intellij.terminal.session.TerminalAliasesInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalAliasesStorage {

  private var aliasesInfo: TerminalAliasesInfo? = null

  fun setAliasesInfo(newAliasesInfo: TerminalAliasesInfo) {
    if (this.aliasesInfo == null) {
      this.aliasesInfo = newAliasesInfo
    }
  }

  fun getAliasesInfo(): TerminalAliasesInfo {
    return aliasesInfo ?: TerminalAliasesInfo(aliases = emptyMap())
  }

  companion object {
    val KEY: Key<TerminalAliasesStorage> = Key.create("TerminalAliasesStorage")
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.service

/**
 * Terminal options with [RoamingType.LOCAL].
 */
interface TerminalLocalOptions {
  var shellPath: String?

  companion object {
    @JvmStatic
    fun getInstance(): TerminalLocalOptions = service()
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings

import com.intellij.openapi.components.service

/**
 * Terminal options with [com.intellij.openapi.components.RoamingType.PER_OS]
 */
interface TerminalOsSpecificOptions {
  /**
   * Whether text should be automatically put into clipboard when selecting it.
   *
   * By default, this option is enabled only on Linux because it has separate clipboard for selection.
   */
  var copyOnSelection: Boolean

  companion object {
    @JvmStatic
    fun getInstance(): TerminalOsSpecificOptions = service()
  }
}
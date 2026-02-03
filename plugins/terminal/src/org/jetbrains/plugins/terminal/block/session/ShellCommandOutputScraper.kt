// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable

internal interface ShellCommandOutputScraper {

  fun scrapeOutput(): StyledCommandOutput

  /**
   * @param useExtendedDelayOnce whether to send first content update with greater delay than default
   */
  fun addListener(listener: ShellCommandOutputListener, parentDisposable: Disposable, useExtendedDelayOnce: Boolean = false)

}
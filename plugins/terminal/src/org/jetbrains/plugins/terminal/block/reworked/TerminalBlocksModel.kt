// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalBlocksModel {
  /** The list can be mutable in the implementation, so it should not be cached. */
  @get:RequiresEdt
  val blocks: List<TerminalBlockBase>

  fun addListener(parentDisposable: Disposable, listener: TerminalBlocksModelListener)

  companion object {
    val KEY: Key<TerminalBlocksModel> = Key.create("TerminalBlocksModel")
  }
}
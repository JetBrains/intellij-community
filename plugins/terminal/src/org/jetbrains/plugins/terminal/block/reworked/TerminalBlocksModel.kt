// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.util.Key
import com.intellij.terminal.session.TerminalBlocksModelState
import com.intellij.terminal.session.TerminalOutputBlock
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalBlocksModel {
  /** The list can be mutable in the implementation, so it should not be cached. */
  @get:RequiresEdt
  val blocks: List<TerminalOutputBlock>

  /**
   * Blocks are guaranteed to be valid if they are requested during this flow collection on EDT.
   */
  val events: SharedFlow<TerminalBlocksModelEvent>

  @RequiresEdt
  fun promptStarted(offset: Int)

  @RequiresEdt
  fun promptFinished(offset: Int)

  @RequiresEdt
  fun commandStarted(offset: Int)

  @RequiresEdt
  fun commandFinished(exitCode: Int)

  @RequiresEdt
  fun dumpState(): TerminalBlocksModelState

  @RequiresEdt
  fun restoreFromState(state: TerminalBlocksModelState)

  companion object {
    val KEY: Key<TerminalBlocksModel> = Key.create("TerminalBlocksModel")
  }
}
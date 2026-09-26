// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * The model that holds the information about the ranges of the shell output: terminal blocks.
 *
 * Note that the block model exists only in the context
 * of the [regular][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.regular] output model.
 * So, all [org.jetbrains.plugins.terminal.view.TerminalOffset]'s are specified relative to it.
 *
 * The interface provides a read-only view, but the model itself is mutable and therefore should only be accessed on the mutating thread,
 * which is currently the **EDT**.
 *
 * @see TerminalBlockBase
 * @see TerminalCommandBlock
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlocksModel {
  /**
   * The list of the blocks in the model, it is always sorted in the order of appearance.
   * So, the first block is the oldest in the output, and the last block is the newest.
   *
   * The list is always non-empty.
   * The first block may be partially out of the regular [org.jetbrains.plugins.terminal.view.TerminalOutputModel] bounds due to trimming.
   *
   * The list can be mutable in the implementation, so it should not be cached.
   */
  val blocks: List<TerminalBlockBase>

  /**
   * Currently active terminal block.
   * For example, it can contain the current command a user is typing or executing.
   */
  val activeBlock: TerminalBlockBase

  fun addListener(parentDisposable: Disposable, listener: TerminalBlocksModelListener)

  companion object {
    val KEY: Key<TerminalBlocksModel> = Key.create("TerminalBlocksModel")
  }
}
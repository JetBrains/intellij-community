// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Terminal emulators usually provide two models: for the regular output buffer and the alternative one.
 * The alternative model is usually used by "fullscreen" terminal applications like vim, nano, mc and so on.
 * While commands are executed when the regular model is active.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputModelsSet {
  val regular: TerminalOutputModel
  val alternative: TerminalOutputModel

  /**
   * Represents the currently active output model whose content is showing in the terminal.
   */
  val active: StateFlow<TerminalOutputModel>
}
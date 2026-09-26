// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface ChainStatus {
  data object Computing : ChainStatus
  data object LanguageNotSupported : ChainStatus
  data object NotFound : ChainStatus
  /**
   * [position] is the pause point where [chains] were detected
   */
  data class Found(val position: XSourcePosition, val chains: List<StreamChainWithLibrary>) : ChainStatus
}

@ApiStatus.Internal
class StreamChainWithLibrary(@JvmField val chain: StreamChain, @JvmField val provider: LibrarySupportProvider)

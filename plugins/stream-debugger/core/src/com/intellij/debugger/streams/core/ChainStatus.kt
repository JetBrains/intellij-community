// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.wrapper.StreamChain
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface ChainStatus {
  data object Computing : ChainStatus
  data object LanguageNotSupported : ChainStatus
  data object NotFound : ChainStatus
  data class Found(val chains: List<StreamChainWithLibrary>) : ChainStatus
}

@ApiStatus.Internal
class StreamChainWithLibrary(@JvmField val chain: StreamChain, @JvmField val provider: LibrarySupportProvider)

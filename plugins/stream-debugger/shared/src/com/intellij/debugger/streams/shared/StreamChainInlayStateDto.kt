// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.shared

import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * @see com.intellij.debugger.streams.core.StreamChainInlayState
 */
@ApiStatus.Internal
@Serializable
sealed interface StreamChainInlayStateDto {
  @Serializable
  data class Visible(val position: XSourcePositionDto) : StreamChainInlayStateDto

  @Serializable
  data object Hidden : StreamChainInlayStateDto
}

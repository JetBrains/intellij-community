// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.shared

import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * @see com.intellij.debugger.streams.core.ChainStatus
 */
@ApiStatus.Internal
@Serializable
sealed interface ChainStateDto {
  @Serializable
  data object LanguageNotSupported : ChainStateDto
  @Serializable
  data object Computing : ChainStateDto
  @Serializable
  data object NotFound : ChainStateDto
  @Serializable
  data class Found(val position: XSourcePositionDto) : ChainStateDto
}

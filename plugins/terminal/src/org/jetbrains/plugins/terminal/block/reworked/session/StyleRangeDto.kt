// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.session.StyleRange

@ApiStatus.Internal
@Serializable
data class StyleRangeDto(
  val startOffset: Int,
  val endOffset: Int,
  val style: TextStyleDto,
)

@ApiStatus.Internal
fun StyleRange.toDto(): StyleRangeDto = StyleRangeDto(startOffset, endOffset, style.toDto())

@ApiStatus.Internal
fun StyleRangeDto.toStyleRange(): StyleRange = StyleRange(startOffset, endOffset, style.toTextStyle())
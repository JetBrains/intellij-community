// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.StyleRange

@ApiStatus.Internal
@Serializable
data class StyleRangeDto(
  val startOffset: Long,
  val endOffset: Long,
  val style: TextStyleDto,
  val ignoreContrastAdjustment: Boolean,
)

@ApiStatus.Internal
fun StyleRange.toDto(): StyleRangeDto = StyleRangeDto(startOffset, endOffset, style.toDto(), ignoreContrastAdjustment)

@ApiStatus.Internal
fun StyleRangeDto.toStyleRange(): StyleRange = StyleRange(startOffset, endOffset, style.toTextStyle(), ignoreContrastAdjustment)
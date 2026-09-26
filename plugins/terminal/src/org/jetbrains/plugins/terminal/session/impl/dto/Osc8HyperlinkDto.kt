// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink

@ApiStatus.Internal
@Serializable
data class Osc8HyperlinkDto(
  val startOffset: Long,
  val endOffset: Long,
  val uri: String,
)

@ApiStatus.Internal
fun Osc8Hyperlink.toDto(): Osc8HyperlinkDto = Osc8HyperlinkDto(startOffset, endOffset, uri)

@ApiStatus.Internal
fun Osc8HyperlinkDto.toOsc8Hyperlink(): Osc8Hyperlink = Osc8Hyperlink(startOffset, endOffset, uri)

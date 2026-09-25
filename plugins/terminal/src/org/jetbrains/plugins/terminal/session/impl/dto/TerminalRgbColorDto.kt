// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
@Serializable
data class TerminalRgbColorDto(val red: Int, val green: Int, val blue: Int)

@ApiStatus.Internal
fun Color.toRgbColorDto(): TerminalRgbColorDto = TerminalRgbColorDto(red, green, blue)

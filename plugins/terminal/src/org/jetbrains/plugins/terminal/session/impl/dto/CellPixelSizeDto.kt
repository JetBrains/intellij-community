// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The size of one grid cell in pixels.
 * See `com.intellij.terminal.emulator.CellPixelSize`.
 */
@ApiStatus.Internal
@Serializable
data class CellPixelSizeDto(val width: Int, val height: Int)

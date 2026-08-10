// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.shared

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * @see com.intellij.debugger.streams.core.statistics.TraceEntryPoint
 */
@ApiStatus.Internal
@Serializable
enum class TraceEntryPointDto {
  TOOLBAR_ACTION,
  INLAY_HINT
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.common.tools

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Each tool must have unique id i.e: `uv`
 */
@JvmInline
@ApiStatus.Internal
value class ToolId(val id: @NonNls String)
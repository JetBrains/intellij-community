package com.intellij.python.pyproject.model.spi

import org.jetbrains.annotations.NonNls

/**
 * Each tool must have unique id i.e: `uv`
 */
@JvmInline
value class ToolId(val id: @NonNls String)
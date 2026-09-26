// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.breakpoints

import com.intellij.openapi.editor.ex.RangeHighlighterEx

object MinimapBreakpointUtil {
  const val BREAKPOINT_HIGHLIGHTER_LAYER: Int = 2980
  private const val BREAKPOINT_RENDERER_NAME_TOKEN = "breakpoint"

  fun isBreakpointHighlighter(highlighter: RangeHighlighterEx): Boolean {
    if (highlighter.layer != BREAKPOINT_HIGHLIGHTER_LAYER) return false
    val rendererClassName = highlighter.gutterIconRenderer?.javaClass?.name ?: return false
    return rendererClassName.contains(BREAKPOINT_RENDERER_NAME_TOKEN, ignoreCase = true)
  }
}

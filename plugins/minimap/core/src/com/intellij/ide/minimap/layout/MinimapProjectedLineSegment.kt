// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

internal data class MinimapProjectedLineSegment(
  val lineStartOffset: Int,
  val lineEndOffset: Int,
  val startOffset: Int,
  val endOffset: Int,
  private val startColumn: Int,
) {
  fun visualColumn(offset: Int): Int {
    val clampedOffset = offset.coerceIn(startOffset, endOffset)
    return startColumn + (clampedOffset - startOffset).coerceAtLeast(0)
  }
}

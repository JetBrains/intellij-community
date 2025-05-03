// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff.util

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.Side
import com.intellij.lang.properties.diff.data.SemiOpenLineRange
import com.intellij.openapi.util.TextRange


internal fun LineFragment.toLineRange(side: Side): SemiOpenLineRange = when (side) {
  Side.LEFT -> SemiOpenLineRange(startLine1, endLine1)
  Side.RIGHT -> SemiOpenLineRange(startLine2, endLine2)
}

internal fun LineFragment.toLastLineRange(oppositeSide: Side): SemiOpenLineRange = when (oppositeSide) {
  Side.LEFT -> SemiOpenLineRange(endLine2, endLine2)
  Side.RIGHT -> SemiOpenLineRange(endLine1, endLine1)
}

internal fun List<LineFragment>.toTextRange(side: Side): List<TextRange> {
  return map { fragment -> fragment.toTextRange(side) }
}

private fun LineFragment.toTextRange(side: Side): TextRange = when (side) {
  Side.LEFT -> TextRange(startOffset1, endOffset1)
  Side.RIGHT -> TextRange(startOffset2, endOffset2)
}
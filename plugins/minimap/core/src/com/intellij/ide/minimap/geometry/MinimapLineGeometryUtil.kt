// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.geometry

object MinimapLineGeometryUtil {
  fun baseLineHeight(lineCount: Int, minimapHeight: Int): Double {
    return minimapHeight.toDouble() / lineCount
  }

  fun lineGap(baseLineHeight: Double): Double {
    return (baseLineHeight * 0.5).coerceAtMost(2.0)
  }

  fun lineTop(line: Int, baseLineHeight: Double): Double {
    return line * baseLineHeight
  }

  fun lineHeight(baseLineHeight: Double, lineGap: Double): Double {
    return (baseLineHeight - lineGap).coerceAtLeast(1.0)
  }
}

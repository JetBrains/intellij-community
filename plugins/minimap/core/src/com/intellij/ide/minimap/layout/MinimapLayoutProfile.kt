// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

/**
 * Per-editor minimap layout profile.
 *
 * The profile allows IDEs/plugins to tune minimap composition for specific file types, for example:
 * - widen the leading gutter area;
 * - reserve extra panel width to keep content readable after widening the gutter.
 */
data class MinimapLayoutProfile(
  /**
   * Width of the leading minimap gutter area, in pixels.
   */
  val leadingGutterWidthPx: Int = DEFAULT_LEADING_GUTTER_WIDTH_PX,
  /**
   * Extra width appended to the computed minimap width, in pixels.
   */
  val additionalPanelWidthPx: Int = 0,
  /**
   * Minimum content width that remains available after gutter/inset is applied.
   */
  val minContentWidthPx: Int = DEFAULT_MIN_CONTENT_WIDTH_PX,
) {
  fun normalized(): MinimapLayoutProfile {
    return MinimapLayoutProfile(
      leadingGutterWidthPx = leadingGutterWidthPx.coerceAtLeast(0),
      additionalPanelWidthPx = additionalPanelWidthPx.coerceAtLeast(0),
      minContentWidthPx = minContentWidthPx.coerceAtLeast(1),
    )
  }

  companion object {
    const val DEFAULT_LEADING_GUTTER_WIDTH_PX: Int = 6
    const val DEFAULT_MIN_CONTENT_WIDTH_PX: Int = 1
    val DEFAULT: MinimapLayoutProfile = MinimapLayoutProfile()
  }
}

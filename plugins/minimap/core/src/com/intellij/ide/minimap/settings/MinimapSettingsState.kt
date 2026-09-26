// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.util.ui.JBUI

/**
 * @param enabled User-facing global visibility switch. Product-specific defaults are applied by [MinimapSettings].
 * @param width Fixed width (scaled).
 * @param rightAligned If false, Minimap will be on the left side.
 * @param insideScrollbar If true and [rightAligned] is true, the vertical scrollbar stays to the right of the minimap.
 * @param showHover If true, structure hover popups are shown over supported minimaps.
 *
 * The set of file types that support the minimap is determined by the
 * [com.intellij.ide.minimap.model.MinimapFileSupportPolicy] extension point rather than
 * stored here. By default the minimap is shown for all non-binary text files.
 */
data class MinimapSettingsState(var enabled: Boolean = false,
                                var width: Int = FIXED_WIDTH,
                                var scaleMode: MinimapScaleMode = MinimapScaleMode.FILL,
                                var rightAligned: Boolean = true,
                                var insideScrollbar: Boolean = true,
                                var showHover: Boolean = true) {
  companion object {
    val FIXED_WIDTH: Int = JBUI.scale(120)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.render.MinimapRenderEntry
import javax.swing.Icon
import java.awt.Rectangle

data class MinimapHoverTarget(
  val entry: MinimapRenderEntry,
  val rect: Rectangle,
  val text: String,
  val icon: Icon?,
  val declarationWidth: Int,
) {
  fun sameAs(other: MinimapHoverTarget?): Boolean {
    if (other == null) return false

    return entry.isSameEntry(other.entry) &&
           text == other.text &&
           icon === other.icon &&
           rect == other.rect
  }
}

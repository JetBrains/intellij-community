// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

import java.awt.Graphics2D
import org.jetbrains.annotations.ApiStatus

@ApiStatus.OverrideOnly
interface MinimapLayer {
  val id: MinimapLayerId
  val order: Int

  fun isApplicable(state: MinimapLayerRenderState): Boolean = true

  fun paint(graphics: Graphics2D, state: MinimapLayerRenderState)
}

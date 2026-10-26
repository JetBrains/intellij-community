// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Rectangle

@ApiStatus.Internal
@ApiStatus.Experimental
interface CodeFenceRenderingService {
  /** Converts [source] to [CodeFencePainter] for given [language]. Returns null when operation can't be performed. */
  suspend fun render(language: String, source: String): CodeFencePainter?

  companion object {
    val EP_NAME: ExtensionPointName<CodeFenceRenderingService> = ExtensionPointName("org.intellij.markdown.codeFenceRenderingService")

    suspend fun render(language: String, source: String): CodeFencePainter? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.render(language, source) }
  }
}

/**
 * The [width] and [height] use logical pixels. The caller chooses the `bounds` for the current view and can fit the
 * painter into a smaller rectangle. The painter must keep the object aspect ratio inside the `bounds`.
 */
@ApiStatus.Internal
interface CodeFencePainter {
  /** The natural width in logical pixels. */
  val width: Float

  /** The natural height in logical pixels. */
  val height: Float

  /** Paints the object inside [bounds]. The rectangle is in the coordinates of [graphics]. */
  fun paint(graphics: Graphics2D, bounds: Rectangle)
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.ide.structureView.StructureViewTreeElement
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.lang.ref.SoftReference

class MinimapRenderEntry private constructor(
  internal val elementReference: SoftReference<StructureViewTreeElement>?,
  val rect2d: Rectangle2D.Double,
  val color: Color? = null,
  val sampleOffset: Int = NO_SAMPLE_OFFSET,
) {
  constructor(
    element: StructureViewTreeElement?,
    rect2d: Rectangle2D.Double,
    color: Color? = null,
    sampleOffset: Int = NO_SAMPLE_OFFSET,
  ) : this(element?.let(::SoftReference), rect2d, color, sampleOffset)

  val element: StructureViewTreeElement?
    get() = elementReference?.get()

  fun isSameEntry(other: MinimapRenderEntry?): Boolean {
    if (other == null) return false
    val element = element
    val otherElement = other.element
    return if (element != null && otherElement != null) element === otherElement else elementReference === other.elementReference
  }

  companion object {
    const val NO_SAMPLE_OFFSET: Int = -1

    internal fun forStructureElement(
      elementReference: SoftReference<StructureViewTreeElement>,
      rect2d: Rectangle2D.Double,
      sampleOffset: Int,
    ): MinimapRenderEntry = MinimapRenderEntry(elementReference, rect2d, sampleOffset = sampleOffset)
  }
}

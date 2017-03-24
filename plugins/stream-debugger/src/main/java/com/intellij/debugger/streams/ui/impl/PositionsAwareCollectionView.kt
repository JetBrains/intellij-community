package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.ui.ValueWithPosition
import java.awt.Graphics

/**
 * @author Vitaliy.Bibaev
 */
class PositionsAwareCollectionView(header: String,
                                   evaluationContext: EvaluationContextImpl,
                                   private val values: List<ValueWithPosition>) : CollectionView(header, evaluationContext,
                                                                                                 values.map { it.traceElement }) {
  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)

    val visibleRect = instancesTree.visibleRect
    for (value in values) {
      val rect = instancesTree.getRectByValue(value.traceElement)
      if (rect == null || !visibleRect.intersects(rect)) {
        value.position = -1
        value.isSelected = false
      }
      else {
        value.position = rect.y + rect.height / 2 - visibleRect.y
        value.isSelected = instancesTree.isSelected(value.traceElement)
      }
    }
  }
}


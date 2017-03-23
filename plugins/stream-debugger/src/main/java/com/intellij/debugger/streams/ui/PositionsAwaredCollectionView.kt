package com.intellij.debugger.streams.ui

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import java.awt.Graphics

/**
 * @author Vitaliy.Bibaev
 */
class PositionsAwaredCollectionView(header: String,
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
      }
      else {
        value.position = rect.x + rect.height / 2
        value.isSelected = instancesTree.isSelected(value.traceElement)
      }
    }
  }
}


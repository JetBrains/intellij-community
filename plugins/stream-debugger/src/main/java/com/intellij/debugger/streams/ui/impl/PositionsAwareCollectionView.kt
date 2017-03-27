package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.ui.PaintingListener
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.openapi.application.ApplicationManager

/**
 * @author Vitaliy.Bibaev
 */
class PositionsAwareCollectionView(header: String,
                                   evaluationContext: EvaluationContextImpl,
                                   private val values: List<ValueWithPosition>) : CollectionView(header, evaluationContext,
                                                                                                 values.map { it.traceElement }) {
  init {
    instancesTree.addPaintingListener(object : PaintingListener {
      override fun componentPainted() {
        updateValues()
      }
    })
  }

  private fun updateValues(): Unit {
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

    ApplicationManager.getApplication().invokeLater({})
  }
}


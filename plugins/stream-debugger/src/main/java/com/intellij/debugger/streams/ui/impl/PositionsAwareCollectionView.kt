package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.ui.PaintingListener
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher

/**
 * @author Vitaliy.Bibaev
 */
class PositionsAwareCollectionView(header: String,
                                   evaluationContext: EvaluationContextImpl,
                                   private val values: List<ValueWithPositionImpl>) : CollectionView(header, evaluationContext,
                                                                                                     values.map { it.traceElement }) {
  private val myDispatcher: EventDispatcher<ValuesPositionsListener> = EventDispatcher.create(ValuesPositionsListener::class.java)

  init {
    instancesTree.addPaintingListener(object : PaintingListener {
      override fun componentPainted() {
        updateValues()
      }
    })
  }

  fun addValuesPositionsListener(listener: ValuesPositionsListener): Unit = myDispatcher.addListener(listener)

  private fun updateValues(): Unit {
    var changed: Boolean = false
    val visibleRect = instancesTree.visibleRect
    for (value in values) {
      val rect = instancesTree.getRectByValue(value.traceElement)
      if (rect == null) {
        changed = value.invalidate(changed)
      }
      else {
        changed = value.set(changed,
                            rect.y + rect.height / 2 - visibleRect.y,
                            visibleRect.intersects(rect),
                            instancesTree.isSelected(value.traceElement))
      }
    }

    if (changed) {
      ApplicationManager.getApplication().invokeLater({ myDispatcher.multicaster.valuesPositionsChanged() })
    }
  }

  private fun ValueWithPositionImpl.invalidate(modified: Boolean): Boolean {
    return when (modified) {
      true -> {
        setInvalid()
        true
      }
      false -> updateToInvalid()
    }
  }

  private fun ValueWithPositionImpl.set(modified: Boolean, pos: Int, visible: Boolean, selected: Boolean): Boolean {
    return when (modified) {
      true -> {
        setProperties(pos, visible, selected)
        true
      }
      false -> updateProperties(pos, visible, selected)
    }
  }
}




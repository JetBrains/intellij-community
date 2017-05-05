/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.PaintingListener
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher
import com.sun.jdi.Value

/**
 * @author Vitaliy.Bibaev
 */
open class PositionsAwareCollectionView(header: String,
                                        evaluationContext: EvaluationContextImpl,
                                        rawValues: List<Value>,
                                        private val values: List<ValueWithPositionImpl>)
  : CollectionView(header, evaluationContext, rawValues, values.map { it.traceElement }) {
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
                            instancesTree.isHighlighted(value.traceElement))
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

  private fun ValueWithPositionImpl.set(modified: Boolean, pos: Int, visible: Boolean, highlighted: Boolean): Boolean {
    return when (modified) {
      true -> {
        setProperties(pos, visible, highlighted)
        true
      }
      false -> updateProperties(pos, visible, highlighted)
    }
  }
}

class SourceView(evaluationContext: EvaluationContextImpl, traceElements: List<TraceElement>)
  : CollectionView("Source", evaluationContext, traceElements.map { it.value }, traceElements)

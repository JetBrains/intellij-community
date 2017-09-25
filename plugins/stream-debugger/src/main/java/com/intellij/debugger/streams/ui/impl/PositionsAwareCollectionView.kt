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

import com.intellij.debugger.streams.ui.PaintingListener
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher

/**
 * @author Vitaliy.Bibaev
 */
open class PositionsAwareCollectionView(header: String,
                                        tree: CollectionTree,
                                        private val values: List<ValueWithPositionImpl>)
  : CollectionView(header, tree) {
  private val myDispatcher: EventDispatcher<ValuesPositionsListener> = EventDispatcher.create(ValuesPositionsListener::class.java)

  init {
    instancesTree.addPaintingListener(object : PaintingListener {
      override fun componentPainted() {
        updateValues()
      }
    })
  }

  fun addValuesPositionsListener(listener: ValuesPositionsListener) = myDispatcher.addListener(listener)

  private fun updateValues() {
    var changed = false
    val visibleRect = instancesTree.visibleRect
    for (value in values) {
      val rect = instancesTree.getRectByValue(value.traceElement)
      changed = if (rect == null) {
        value.invalidate(changed)
      }
      else {
        value.set(changed,
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

class SourceView(tree: CollectionTree)
  : CollectionView("Source", tree)

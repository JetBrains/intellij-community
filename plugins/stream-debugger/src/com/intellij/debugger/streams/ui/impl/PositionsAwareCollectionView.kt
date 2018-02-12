// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.ui.PaintingListener
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher

/**
 * @author Vitaliy.Bibaev
 */
open class PositionsAwareCollectionView(tree: CollectionTree,
                                        private val values: List<ValueWithPositionImpl>)
  : CollectionView(tree) {
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
        value.set(changed, rect.y + rect.height / 2 - visibleRect.y,
                  visibleRect.intersects(rect), instancesTree.isHighlighted(value.traceElement))
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

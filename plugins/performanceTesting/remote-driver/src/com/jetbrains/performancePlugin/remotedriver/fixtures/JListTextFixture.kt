// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.SelectablePanel
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.BasicComponentFinder
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJListCellReader
import org.assertj.swing.driver.CellRendererReader
import org.assertj.swing.fixture.JListFixture
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class JListTextFixture(robot: Robot, component: JList<*>) : JListFixture(robot, component) {
  init {
    replaceCellReader(BasicJListCellReader(TextCellRendererReader()))
  }

  fun replaceCellRendererReader(reader: CellRendererReader) {
    replaceCellReader(BasicJListCellReader(reader))
  }

  fun collectRawItems(): List<String> = computeOnEdt {
    val model = target().model
    List(model.size) { model.getElementAt(it).toString() }
  }
  fun collectItems(): List<String> = contents().toList()
  fun collectSelectedItems(): List<String> = selection().toList()
  fun clickItemAtIndex(index: Int) {
    clickItem(index)
  }

  fun collectIconsAtIndex(index: Int): List<String> {
    val itemComponent = getComponentAtIndex(index)
    if (itemComponent is Container) {
      return BasicComponentFinder.finderWithCurrentAwtHierarchy().findAll(itemComponent) { it is JLabel && it.icon != null }.map {
        (it as JLabel).icon.toString()
      }
    }
    return emptyList()
  }

  fun getComponentAtIndex(index: Int): Component {
    return computeOnEdt { renderComponentAtIndex(index) }
  }

  fun visibleBackgroundAtIndex(index: Int): String = computeOnEdt {
    val component = renderComponentAtIndex(index)

    (component as? Container)?.components?.firstOrNull {
      it is SelectablePanel
    }?.let {
      it.background?.toString()
    }
    ?: component.background?.toString()
    ?: ""
  }

  private fun renderComponentAtIndex(index: Int): Component {
    val list = target()
    @Suppress("UNCHECKED_CAST") val renderer = list.cellRenderer as ListCellRenderer<Any>
    return renderer.getListCellRendererComponent(JList(), list.model.getElementAt(index), index, list.isSelectedIndex(index), list.hasFocus() && list.isSelectedIndex(index))
  }

  fun getTextAttributes(index: Int): List<Pair<String, SimpleTextAttributes>> {
    val component = getComponentAtIndex(index)
    return component.collectTextAttributes(robot())
  }
}
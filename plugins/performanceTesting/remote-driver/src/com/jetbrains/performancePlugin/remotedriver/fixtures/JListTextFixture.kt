// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.BasicComponentFinder
import org.assertj.swing.core.GenericTypeMatcher
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
    return computeOnEdt {
      val list = target()
      @Suppress("UNCHECKED_CAST") val renderer = list.cellRenderer as ListCellRenderer<Any>
      renderer.getListCellRendererComponent(JList(), list.model.getElementAt(index), index, list.isSelectedIndex(index), list.hasFocus() && list.isSelectedIndex(index))
    }
  }

  fun getTextAttributes(index: Int): List<Pair<String, SimpleTextAttributes>> {
    val component = getComponentAtIndex(index)
    return collectTextAttributes(component)
  }

  private fun collectTextAttributes(component: Component): List<Pair<String, SimpleTextAttributes>> {
    val result = mutableListOf<Pair<String, SimpleTextAttributes>>()

    if (component is SimpleColoredComponent) {
      result.addAll(component.collectTextAttributes())
    }
    else if (component is Container) {
      val components = robot().finder().findAll(component, object : GenericTypeMatcher<SimpleColoredComponent>(SimpleColoredComponent::class.java) {
        override fun isMatching(component: SimpleColoredComponent): Boolean {
          return true
        }
      })
      for (c in components) {
        result.addAll(c.collectTextAttributes())
      }
    }

    return result
  }

  private fun SimpleColoredComponent.collectTextAttributes(): List<Pair<String, SimpleTextAttributes>> = buildList {
    val iter = this@collectTextAttributes.iterator()
    while (iter.hasNext()) {
      val text = iter.next()
      add(text to iter.textAttributes)
    }
  }
}
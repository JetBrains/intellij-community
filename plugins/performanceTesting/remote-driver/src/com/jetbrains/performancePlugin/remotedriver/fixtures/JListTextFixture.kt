package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.jetbrains.performancePlugin.remotedriver.dataextractor.JListTextCellReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.BasicComponentFinder
import org.assertj.swing.core.Robot
import org.assertj.swing.fixture.JListFixture
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class JListTextFixture(robot: Robot, component: JList<*>) : JListFixture(robot, component) {
  init {
    replaceCellReader(JListTextCellReader())
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
    val itemComponent = computeOnEdt {
      val list = target()
      @Suppress("UNCHECKED_CAST") val renderer = list.cellRenderer as ListCellRenderer<Any>
      renderer.getListCellRendererComponent(JList(), list.model.getElementAt(index), index, list.isSelectedIndex(index), false)
    }
    if (itemComponent is Container) {
      return BasicComponentFinder.finderWithCurrentAwtHierarchy().findAll(itemComponent) { it is JLabel && it.icon != null }.map {
        (it as JLabel).icon.toString()
      }
    }
    return emptyList()
  }
}
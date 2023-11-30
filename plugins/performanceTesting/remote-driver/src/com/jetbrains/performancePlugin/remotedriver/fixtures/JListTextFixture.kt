package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.jetbrains.performancePlugin.remotedriver.dataextractor.JListTextCellReader
import org.assertj.swing.core.Robot
import org.assertj.swing.fixture.JListFixture
import javax.swing.JList

class JListTextFixture(robot: Robot, component: JList<*>) : JListFixture(robot, component) {
  init {
    replaceCellReader(JListTextCellReader())
  }

  fun collectItems(): List<String> = contents().toList()
  fun collectSelectedItems(): List<String> = selection().toList()
  fun clickItemAtIndex(index: Int) {
    clickItem(index)
  }
}
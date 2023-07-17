package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.StringList
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JListTextCellReader
import org.assertj.swing.core.Robot
import org.assertj.swing.fixture.JListFixture
import javax.swing.JList

class JListTextFixture(robot: Robot, component: JList<*>) : JListFixture(robot, component) {
  init {
    replaceCellReader(JListTextCellReader())
  }

  fun collectItems() = StringList().apply { addAll(contents()) }
  fun collectSelectedItems() = StringList().apply { addAll(selection()) }
  fun clickItemAtIndex(index: Int) {
    clickItem(index)
  }
}
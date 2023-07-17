package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.StringList
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JComboBoxTextCellReader
import org.assertj.swing.core.Robot
import org.assertj.swing.fixture.JComboBoxFixture
import javax.swing.JComboBox

class JComboBoxTextFixture(robot: Robot, component: JComboBox<*>) : JComboBoxFixture(robot, component) {
  init {
    replaceCellReader(JComboBoxTextCellReader())
  }

  /*
  Returns selected text or empty String if selectedText is null
   */

  fun select(text: String) { selectItem(text) }
  fun selectedText(): String = selectedItem() ?: ""

  fun listValues(): StringList {
    return StringList().apply { addAll(contents()) }
  }
}
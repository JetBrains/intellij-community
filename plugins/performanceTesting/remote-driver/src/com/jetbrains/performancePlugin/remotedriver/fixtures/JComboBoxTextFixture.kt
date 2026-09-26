// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJComboBoxCellReader
import org.assertj.swing.driver.CellRendererReader
import org.assertj.swing.driver.ComponentPreconditions
import org.assertj.swing.fixture.JComboBoxFixture
import javax.swing.JComboBox

class JComboBoxTextFixture(robot: Robot, component: JComboBox<*>) : JComboBoxFixture(robot, component) {
  init {
    replaceCellReader(BasicJComboBoxCellReader(TextCellRendererReader()))
  }

  fun replaceCellRendererReader(reader: CellRendererReader) {
    replaceCellReader(BasicJComboBoxCellReader(reader))
  }

  fun select(text: String) {
    checkEnabledAndShowing()
    showDropDownList()
    select(findItemIndex(text))
    hideDropDownList()
  }

  fun selectedText(): String = selectedItem() ?: ""

  fun listValues(): List<String> {
    return contents().toList()
  }

  fun getItemCount(): Int = computeOnEdt { target().itemCount }

  private fun select(index: Int) {
    require(index in 0..<getItemCount()) { "Index $index is out of range [0, ${getItemCount()}]" }
    computeOnEdt {
      target().selectedIndex = index
    }
    robot().waitForIdle()
  }

  private fun checkEnabledAndShowing() {
    computeOnEdt {
      ComponentPreconditions.checkEnabledAndShowing(target())
    }
  }

  private fun showDropDownList() {
    computeOnEdt { target().showPopup() }
    robot().waitForIdle()
  }

  private fun hideDropDownList() {
    computeOnEdt { target().hidePopup() }
    robot().waitForIdle()
  }

  private fun findItemIndex(text: String): Int {
    val items = listValues()
    return checkNotNull(items.indexOf(text).takeIf { it >= 0 }) { "No such item in combobox: $text, contents: $items" }
  }
}
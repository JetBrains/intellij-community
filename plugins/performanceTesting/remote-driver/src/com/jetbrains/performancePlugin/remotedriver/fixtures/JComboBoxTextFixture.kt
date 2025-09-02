// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJComboBoxCellReader
import org.assertj.swing.driver.CellRendererReader
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
    selectItem(text)
  }

  fun selectedText(): String = selectedItem() ?: ""

  fun listValues(): List<String> {
    return contents().toList()
  }
}
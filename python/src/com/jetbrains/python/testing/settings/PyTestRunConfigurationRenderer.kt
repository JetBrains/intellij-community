// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.settings

import com.intellij.ui.components.JBLabel
import com.jetbrains.python.testing.PyAbstractTestFactory
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class PyTestRunConfigurationRenderer : ListCellRenderer<PyAbstractTestFactory<*>> {
  override fun getListCellRendererComponent(list: JList<out PyAbstractTestFactory<*>>,
                                            value: PyAbstractTestFactory<*>,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component = JBLabel(value.name)
}

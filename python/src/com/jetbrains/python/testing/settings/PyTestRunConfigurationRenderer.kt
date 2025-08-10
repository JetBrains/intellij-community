// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBInsets
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectionConfigurationFactory
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class PyTestRunConfigurationRenderer(private val sdk: Sdk?, val project: Project) : ListCellRenderer<PyAbstractTestFactory<*>> {
  private val autoFactory: PyAutoDetectionConfigurationFactory = PythonTestConfigurationType.getInstance().autoDetectFactory
  override fun getListCellRendererComponent(list: JList<out PyAbstractTestFactory<*>>,
                                            value: PyAbstractTestFactory<*>,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    if (value == autoFactory && sdk != null) {
      val detectedName = autoFactory.getFactory(sdk, project).name
      return SimpleColoredComponent().apply {
        isOpaque = false
        ipad = JBInsets.create(0, 0)
        append(autoFactory.name).append(" ($detectedName) ", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
    }
    else {
      return JBLabel(value.name)
    }
  }
}

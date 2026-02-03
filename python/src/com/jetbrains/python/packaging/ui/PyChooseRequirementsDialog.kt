/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.ui

import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.ElementsChooser.ElementsMarkListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.jetbrains.python.PyBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class PyChooseRequirementsDialog<T : Any>(project: Project,
                                          requirements: List<T>,
                                          presenter: (T) -> String) : DialogWrapper(project, false) {
  private val myRequirementsChooser: ElementsChooser<T>

  init {
    title = PyBundle.message("python.packaging.choose.packages.to.install")
    setOKButtonText(PyBundle.message("python.packaging.install"))
    myRequirementsChooser = object : ElementsChooser<T>(true) {
      public override fun getItemText(requirement: T): String {
        @Suppress("HardCodedStringLiteral") // it's package name
        return presenter(requirement)
      }
    }
    myRequirementsChooser.setElements(requirements, true)
    myRequirementsChooser.addElementsMarkListener(object : ElementsMarkListener<T> {
      override fun elementMarkChanged(element: T, isMarked: Boolean) {
        isOKActionEnabled = !myRequirementsChooser.getMarkedElements().isEmpty()
      }
    })
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val panel = JPanel(BorderLayout())
    panel.preferredSize = Dimension(400, 300)
    val label = JBLabel(PyBundle.message("choose.packages.to.install"))
    label.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
    panel.add(label, BorderLayout.NORTH)
    panel.add(myRequirementsChooser, BorderLayout.CENTER)
    return panel
  }

  val markedElements: List<T>
    get() = myRequirementsChooser.markedElements
}

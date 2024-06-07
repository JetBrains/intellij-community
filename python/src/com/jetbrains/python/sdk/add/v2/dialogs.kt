// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent


class PythonAddLocalInterpreterDialog(project: Project) : DialogWrapper(project) {
    val outerPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      preferredSize = Dimension(500, 250) // todo scale dimensions
    }

  private lateinit var model: PythonLocalAddInterpreterModel
  private lateinit var mainPanel: PythonAddCustomInterpreter

  init {
    title = "Add Python Interpreter"
    isResizable = true
    init()

    outerPanel.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        val basePath = project.basePath!!
        model = PythonLocalAddInterpreterModel(service<PythonAddSdkService>().coroutineScope,
                                                   Dispatchers.EDT + ModalityState.current().asContextElement(), AtomicProperty(basePath))
        model.navigator.selectionMode = AtomicProperty(PythonInterpreterSelectionMode.CUSTOM)
        mainPanel = PythonAddCustomInterpreter(model)

        val dialogPanel = panel {
          mainPanel.buildPanel(this, WHEN_PROPERTY_CHANGED(AtomicProperty(basePath)))
        }

        dialogPanel.registerValidators(myDisposable) { validations ->
          val anyErrors = validations.entries.any { (key, value) -> key.isVisible && !value.okEnabled }
          isOKActionEnabled = !anyErrors
        }

        outerPanel.add(dialogPanel)

        model.scope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
          model.initialize()
          mainPanel.onShown()
        }
      }
    })

  }


  override fun doOKAction() {
    super.doOKAction()
    val sdk = mainPanel.getSdk()
    if (sdk != null) {
      val existing = ProjectJdkTable.getInstance().findJdk(sdk.name)
      SdkConfigurationUtil.addSdk(sdk)
    }
  }

  override fun createCenterPanel(): JComponent = outerPanel
}
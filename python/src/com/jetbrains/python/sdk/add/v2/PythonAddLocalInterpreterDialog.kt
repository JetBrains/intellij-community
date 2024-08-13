// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent


/**
 * @see PythonAddLocalInterpreterPresenter
 */
class PythonAddLocalInterpreterDialog(private val dialogPresenter: PythonAddLocalInterpreterPresenter) : DialogWrapper(dialogPresenter.moduleOrProject.project) {
  val outerPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    preferredSize = Dimension(500, 250) // todo scale dimensions
  }

  private lateinit var model: PythonLocalAddInterpreterModel
  private lateinit var mainPanel: PythonAddCustomInterpreter

  init {
    title = PyBundle.message("python.sdk.add.python.interpreter.title")
    isResizable = true
    init()

    outerPanel.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        val basePath = dialogPresenter.pathForVEnv.toString()
        model = PythonLocalAddInterpreterModel(PyInterpreterModelParams(service<PythonAddSdkService>().coroutineScope,
                                               Dispatchers.EDT + ModalityState.current().asContextElement(), AtomicProperty(basePath)))
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


  @RequiresEdt
  override fun doOKAction() {
    super.doOKAction()
    runWithModalProgressBlocking(dialogPresenter.moduleOrProject.project, "...") {
      dialogPresenter.okClicked(mainPanel.currentSdkManager)
    }
  }

  override fun createCenterPanel(): JComponent = outerPanel
}
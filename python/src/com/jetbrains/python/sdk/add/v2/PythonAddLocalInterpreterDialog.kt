// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.supervisorScope
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent


/**
 * @see PythonAddLocalInterpreterPresenter
 */
internal class PythonAddLocalInterpreterDialog(private val dialogPresenter: PythonAddLocalInterpreterPresenter) : DialogWrapper(dialogPresenter.moduleOrProject.project) {

  private lateinit var mainPanel: PythonAddCustomInterpreter
  private lateinit var model: PythonLocalAddInterpreterModel

  private val basePath = dialogPresenter.pathForVEnv

  init {
    title = PyBundle.message("python.sdk.add.python.interpreter.title")
    setSize(640, 320)
    isResizable = true
    init()
  }

  override fun getHelpId(): @NonNls String {
    return "create.python.interpreter"
  }

  @RequiresEdt
  override fun doOKAction() {
    super.doOKAction()
    val addEnvironment = mainPanel.currentSdkManager
    PyPackageCoroutine.launch(dialogPresenter.moduleOrProject.project, ModalityState.current().asContextElement()) {
      dialogPresenter.okClicked(addEnvironment)
    }
  }

  override fun createCenterPanel(): JComponent {
    val errorSink = ShowingMessageErrorSync

    val rootPanel = panel {
      model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(basePath))
      model.navigator.selectionMode = AtomicProperty(PythonInterpreterSelectionMode.CUSTOM)
      mainPanel = PythonAddCustomInterpreter(
        model = model,
        module = dialogPresenter.moduleOrProject.moduleIfExists,
        errorSink = errorSink,
        limitExistingEnvironments = false
      )
      mainPanel.setupUI(this, WHEN_PROPERTY_CHANGED(AtomicProperty(basePath)))
    }

    rootPanel.launchOnShow("PythonAddLocalInterpreterDialog launchOnShow") {
      supervisorScope {
        model.initialize(this@supervisorScope)
        mainPanel.onShown(this@supervisorScope)
      }
    }

    return rootPanel
  }
}
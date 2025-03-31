// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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


  @RequiresEdt
  override fun doOKAction() {
    super.doOKAction()
    val addEnvironment = mainPanel.currentSdkManager
    runWithModalProgressBlocking(dialogPresenter.moduleOrProject.project, "...") {
      dialogPresenter.okClicked(addEnvironment)
    }
  }

  override fun createCenterPanel(): JComponent {
    val errorSink = ShowingMessageErrorSync
    lateinit var centerPanelPlaceHolder: Placeholder
    val rootPanel = panel {
      row {
        centerPanelPlaceHolder = placeholder().align(Align.FILL)
      }
    }

    rootPanel.launchOnShow("PythonAddLocalInterpreterDialog launchOnShow") {
      centerPanelPlaceHolder.component = panel {
        model = PythonLocalAddInterpreterModel(
          PyInterpreterModelParams(
            this@launchOnShow,
            // At this moment dialog is not displayed, so there is no modality state
            // The whole idea of context passing is doubtful
            Dispatchers.EDT + ModalityState.any().asContextElement(),
            ProjectPathFlows.create(basePath)
          )
        )
        model.navigator.selectionMode = AtomicProperty(PythonInterpreterSelectionMode.CUSTOM)
        mainPanel = PythonAddCustomInterpreter(model, moduleOrProject = dialogPresenter.moduleOrProject, errorSink = errorSink)
        mainPanel.buildPanel(this, WHEN_PROPERTY_CHANGED(AtomicProperty(basePath)))
      }.apply {
        model.scope.launch(model.uiContext) {
          model.initialize(dialogPresenter.moduleOrProject.project,)
          mainPanel.onShown()
        }
      }
    }

    return rootPanel
  }
}
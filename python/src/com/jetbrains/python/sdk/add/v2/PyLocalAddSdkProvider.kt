// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.PyAddSdkDialogFlowAction
import com.jetbrains.python.sdk.add.PyAddSdkProvider
import com.jetbrains.python.sdk.add.PyAddSdkStateListener
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.util.ShowingMessageErrorSync
import java.awt.Component
import java.nio.file.Path
import javax.swing.Icon

class PyLocalAddSdkProvider : PyAddSdkProvider {
  override fun createView(
    project: Project,
    module: Module?,
    newProjectPath: String?,
    existingSdks: List<Sdk>,
    context: UserDataHolder,
    dialogWrapper: DialogWrapper,
  ): PyAddSdkView {
    return V3AddSdkPanel(
      project = project,
      module = module,
      projectPath = Path.of(newProjectPath ?: project.basePath ?: "."),
      dialogWrapper = dialogWrapper
    )
  }
}


class V3AddSdkPanel(val project: Project, val module: Module?, val projectPath: Path, val dialogWrapper: DialogWrapper) : PyAddSdkView {
  override val panelName: String
    get() = PyBundle.message("python.sdk.local")
  override val icon: Icon
    get() = PythonIcons.Python.Virtualenv

  private val errorSink: ErrorSink = ShowingMessageErrorSync

  private val sdkPanelBuilderAndSdkCreator: PythonSdkPanelBuilderAndSdkCreator = PythonSdkPanelBuilderAndSdkCreator(
    onlyAllowedInterpreterTypes = null,
    errorSink = ShowingMessageErrorSync,
    module = module,
    limitExistingEnvironments = false,
  )

  private val dialogPanel: DialogPanel = panel {
    sdkPanelBuilderAndSdkCreator.buildPanel(this, ProjectPathFlows.create((projectPath)))
  }

  private var validationInfos: List<ValidationInfo> = emptyList()

  init {
    sdkPanelBuilderAndSdkCreator.onShownInitialization(dialogPanel)

    dialogPanel.registerValidators(dialogWrapper.disposable) { compInfos ->
      validationInfos = compInfos.values.toList()
      dialogWrapper.isOKActionEnabled = validationInfos.all { it.okEnabled }
    }
  }

  override fun getOrCreateSdk(): Sdk? {
    val moduleOrProject = if (module != null) ModuleOrProject.ModuleAndProject(module) else ModuleOrProject.ProjectOnly(project)
    val sdk = runWithModalProgressBlocking(project, PyBundle.message("python.sdk.creating.python.sdk")) {
      dialogPanel.apply()
      sdkPanelBuilderAndSdkCreator.getSdk(moduleOrProject).getOr {
        errorSink.emit(it.error)
        return@runWithModalProgressBlocking null
      }
    }
    return sdk?.first
  }

  override fun onSelected() {
    dialogWrapper.isOKActionEnabled = validateAll().isEmpty()
  }

  override val actions: Map<PyAddSdkDialogFlowAction, Boolean>
    get() = mapOf(PyAddSdkDialogFlowAction.OK.enabled())

  override val component: Component
    get() = dialogPanel

  override fun previous(): Unit = throw UnsupportedOperationException()

  override fun next(): Unit = throw UnsupportedOperationException()

  override fun complete(): Unit = Unit

  override fun validateAll(): List<ValidationInfo> = validationInfos

  override fun addStateListener(stateListener: PyAddSdkStateListener): Unit = Unit
}
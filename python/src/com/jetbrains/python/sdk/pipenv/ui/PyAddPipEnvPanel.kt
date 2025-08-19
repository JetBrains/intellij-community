// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv.ui

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.nullize
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.*
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.installSdkIfNeeded
import com.jetbrains.python.sdk.pipenv.*
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent
import kotlin.io.path.pathString

/**
 * The UI panel for adding the pipenv interpreter for the project.
 *
 */
class PyAddPipEnvPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
  private val context: UserDataHolder,
) : PyAddNewEnvPanel() {
  override val envName = "Pipenv"
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.pipenv.environment")
  override val icon: Icon = PIPENV_ICON

  private val moduleField: JComboBox<Module>

  private val baseSdkField = PySdkPathChoosingComboBox()

  init {
    addBaseInterpretersAsync(baseSdkField, existingSdks, module, context)
  }

  private val installPackagesCheckBox = JBCheckBox(PyBundle.message("install.packages.from.pipfile")).apply {
    isVisible = newProjectPath == null
    isSelected = isVisible
  }

  private val pipEnvPathField = TextFieldWithBrowseButton().apply {
    PyPackageCoroutine.launch(project) {
      addBrowseFolderListener(project, withContext(Dispatchers.IO) { FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor() }
        .withTitle(PyBundle.message("python.sdk.pipenv.select.executable.title")))

      val field = textField as? JBTextField ?: return@launch
      detectPipEnvExecutable().getOrNull()?.let {
        field.emptyText.text = PyBundle.message("configurable.pipenv.auto.detected", it.pathString)
      }
      PropertiesComponent.getInstance().pipEnvPath?.let {
        field.text = it
      }
    }
  }

  init {
    layout = BorderLayout()

    val modules = project?.let {
      ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
    } ?: emptyList()

    moduleField = JComboBox(modules.toTypedArray()).apply {
      renderer = ModuleListCellRenderer()
      preferredSize = Dimension(Int.MAX_VALUE, preferredSize.height)
      addItemListener {
        if (it.stateChange == ItemEvent.SELECTED) {
          update()
        }
      }
    }

    pipEnvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        update()
      }
    })

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObjectLabel = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.sdk.pipenv.associated.module")
        }
        else {
          PyBundle.message("python.sdk.pipenv.associated.project")
        }
        addLabeledComponent(associatedObjectLabel, moduleField)
      }
      addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      addComponent(installPackagesCheckBox)
      addLabeledComponent(PyBundle.message("python.sdk.pipenv.executable"), pipEnvPathField)
    }
    add(builder.panel, BorderLayout.NORTH)
    update()
  }

  @RequiresEdt
  override fun getOrCreateSdk(): Sdk? {
    PropertiesComponent.getInstance().pipEnvPath = pipEnvPathField.text.nullize()
    val baseSdk = installSdkIfNeeded(baseSdkField.selectedSdk, selectedModule, existingSdks, context).getOrThrow()

    val moduleBasePath = module?.basePath?.let { Path.of(it) }
                         ?: error("module base path is invalid: ${module?.basePath}")

    val basePythonBinaryPath = baseSdk.homePath?.let { Path.of(it) }
                               ?: error("base python binary path is invalid: ${baseSdk.homePath}")

    return runWithModalProgressBlocking(ModalTaskOwner.guess(), PyBundle.message("python.sdk.setting.up.pipenv.title")) {
      setupPipEnvSdkWithProgressReport(moduleBasePath, existingSdks, basePythonBinaryPath, installPackagesCheckBox.isSelected).onSuccess {
        PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseSdk.homePath
      }
    }.getOrNull()
  }

  override fun getStatisticInfo(): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(type = InterpreterType.PIPENV,
                                     target = InterpreterTarget.LOCAL,
                                     globalSitePackage = false,
                                     makeAvailableToAllProjects = false,
                                     previouslyConfigured = false)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validatePipEnvIsNotAdded())

  override fun addChangeListener(listener: Runnable) {
    pipEnvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener.run()
      }
    })
    super.addChangeListener(listener)
  }

  /**
   * Updates the view according to the current state of UI controls.
   */
  private fun update() {
    PyPackageCoroutine.launch(project) {
      selectedModule?.let {
        installPackagesCheckBox.isEnabled = PipEnvFileHelper.pipFile(it) != null
      }
    }
  }

  /**
   * The effective module for which we add a new environment.
   */
  private val selectedModule: Module?
    get() = module ?: moduleField.selectedItem as? Module

  /**
   * Checks if the pipenv for the project hasn't been already added.
   */
  private fun validatePipEnvIsNotAdded(): ValidationInfo? {
    val path = projectPath ?: return null
    val addedPipEnv = existingSdks.find {
      it.associatedModulePath == path && it.isPipEnv
    } ?: return null
    return ValidationInfo(PyBundle.message("python.sdk.pipenv.has.been.selected", addedPipEnv.name))
  }

  /**
   * The effective project path for the new project or for the existing project.
   */
  private val projectPath: String?
    get() = newProjectPath ?: selectedModule?.basePath ?: project?.basePath
}

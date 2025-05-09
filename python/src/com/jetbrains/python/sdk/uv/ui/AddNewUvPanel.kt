// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.ui

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.PlatformUtils
import com.intellij.util.text.nullize
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.getOrNull
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.add.*
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.UV_ICON
import com.jetbrains.python.sdk.uv.getPyProjectTomlForUv
import com.jetbrains.python.sdk.uv.impl.detectUvExecutable
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnvUnderProgress
import com.jetbrains.python.sdk.uv.validateSdks
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

// TODO: remove old UI support
// FIXME: code duplication w poetry
internal fun allModules(project: Project?): List<Module> {
  return project?.let {
    ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
  }?.sortedBy { it.name } ?: emptyList()
}

/**
 * The UI panel for adding the uv interpreter for the project.
 *
 */
class PyAddNewUvPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
  context: UserDataHolder,
) : PyAddNewEnvPanel() {
  override val envName = "uv"
  override val panelName: String get() = PyBundle.message("python.sdk.uv.environment.panel.title")

  override val icon: Icon = UV_ICON

  private val moduleField: JComboBox<Module>

  private val environmentLocation = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    val path = module?.basePath ?: project?.basePath
    text = when {
      path != null -> path
      else -> System.getProperty("user.home")
    }
  }

  private val baseSdkField = PySdkPathChoosingComboBox()

  init {
    addInterpretersAsync(baseSdkField) {
      validateSdks(module, existingSdks, context)
    }
  }

  private val installPackagesCheckBox = JBCheckBox(PyBundle.message("python.sdk.uv.install.packages.from.toml.checkbox.text")).apply {
    service<PythonSdkCoroutineService>().cs.launch {
      isVisible = projectPath?.let {
        withContext(Dispatchers.IO) {
          StandardFileSystems.local().findFileByPath(it)?.findChild(PY_PROJECT_TOML)?.let { file -> getPyProjectTomlForUv(file) }
        }
      } != null
      isSelected = isVisible
    }
  }

  private val uvPathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor())
    val field = textField as? JBTextField ?: return@apply
    service<PythonSdkCoroutineService>().cs.launch {
      detectUvExecutable()?.let {
        field.emptyText.text = "Auto-detected: ${it.absolutePathString()}"
      }
      getUvExecutable()?.let {
        field.text = it.pathString
      }
    }
  }

  init {
    layout = BorderLayout()

    val modules = allModules(project)

    moduleField = ComboBox(modules.toTypedArray()).apply {
      renderer = ModuleListCellRenderer()
      preferredSize = Dimension(Int.MAX_VALUE, preferredSize.height)
      addItemListener {
        if (it.stateChange == ItemEvent.SELECTED) {
          update()
        }
      }
    }

    uvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        update()
      }
    })

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObjectLabel = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.sdk.uv.associated.module")
        }
        else {
          PyBundle.message("python.sdk.uv.associated.project")
        }
        addLabeledComponent(associatedObjectLabel, moduleField)
      }
      addLabeledComponent(PySdkBundle.message("python.venv.location.label"), environmentLocation)
      addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      addComponent(installPackagesCheckBox)
      addLabeledComponent(PyBundle.message("python.sdk.uv.executable"), uvPathField)
    }

    add(builder.panel, BorderLayout.NORTH)
    update()
  }

  override fun getOrCreateSdk(): Sdk? {
    val path = tryResolvePath(newProjectPath ?: project?.basePath)
    val python = tryResolvePath(baseSdkField.selectedSdk.homePath)

    if (project == null || path == null || python == null) {
      return null
    }

    val uvPath = uvPathField.text.nullize()?.let { Path.of(it) }
    uvPath?.let {
      setUvExecutable(it)
    }

    val sdk = runBlockingCancellable {
      setupNewUvSdkAndEnvUnderProgress(project, path, existingSdks, python)
    }

    sdk.onSuccess {
      PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseSdkField.selectedSdk.homePath
    }

    return sdk.getOrNull()
  }

  override fun getStatisticInfo(): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(type = InterpreterType.UV,
                                     target = InterpreterTarget.LOCAL,
                                     globalSitePackage = false,
                                     makeAvailableToAllProjects = false,
                                     previouslyConfigured = false)
  }

  override fun validateAll(): List<ValidationInfo> =
    emptyList() // Pre-target validation is not supported

  override fun addChangeListener(listener: Runnable) {
    uvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
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
    service<PythonSdkCoroutineService>().cs.launch {
      selectedModule?.let {
        installPackagesCheckBox.isEnabled = PyProjectToml.findFile(it) != null
      }
    }
  }

  /**
   * The effective module for which we add a new environment.
   */
  private val selectedModule: Module?
    get() = module ?: try {
      moduleField.selectedItem
    }
    catch (e: NullPointerException) {
      null
    } as? Module


  /**
   * The effective project path for the new project or for the existing project.
   */
  private val projectPath: String?
    get() = newProjectPath ?: selectedModule?.basePath ?: project?.basePath
}

class PyAddUvSdkProvider : PyAddSdkProvider {
  override fun createView(
    project: Project?,
    module: Module?,
    newProjectPath: String?,
    existingSdks: List<Sdk>,
    context: UserDataHolder,
  ): PyAddSdkPanel {
    val panel = PyAddNewUvPanel(project, module, existingSdks, null, context)
    // TODO: support for adding existing uv environments

    return PyAddSdkGroupPanel(Supplier { "uv environment" }, UV_ICON, listOf(panel), panel)
  }

}
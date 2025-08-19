// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package  com.jetbrains.python.sdk.poetry.ui

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.python.community.impl.poetry.poetryPath
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
import com.jetbrains.python.getOrNull
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.onSuccess
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.poetry.getPyProjectTomlForPoetry
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.ui.pyModalBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent
import kotlin.io.path.absolutePathString

/**
 * The UI panel for adding the poetry interpreter for the project.
 *
 */
class PyAddNewPoetryPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
  context: UserDataHolder,
) : PyAddNewEnvPanel() {
  override val envName = "Poetry"
  override val panelName: String get() = PyBundle.message("python.sdk.poetry.environment.panel.title")

  // TODO: Need a extension point
  override val icon: Icon = POETRY_ICON

  private val moduleField: JComboBox<Module>

  private val baseSdkField = PySdkPathChoosingComboBox()

  init {
    addInterpretersAsync(baseSdkField) {
      validateSdks(module, existingSdks, context)
    }
  }


  private val installPackagesCheckBox = JBCheckBox(PyBundle.message("python.sdk.poetry.install.packages.from.toml.checkbox.text")).apply {
    PyPackageCoroutine.launch(project) {
      isVisible = projectPath?.let {
        withContext(Dispatchers.IO) {
          StandardFileSystems.local().findFileByPath(it)?.findChild(PY_PROJECT_TOML)?.let { file -> getPyProjectTomlForPoetry(file) }
        }
      } != null
      isSelected = isVisible
    }
  }

  private val poetryPathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileDescriptor())
    val field = textField as? JBTextField ?: return@apply
    PyPackageCoroutine.launch(project) {
      detectPoetryExecutable().getOrNull()?.let { field.emptyText.text = "Auto-detected: ${it.absolutePathString()}" }
      PropertiesComponent.getInstance().poetryPath?.let {
        field.text = it
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

    poetryPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        update()
      }
    })

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObjectLabel = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.sdk.poetry.associated.module")
        }
        else {
          PyBundle.message("python.sdk.poetry.associated.project")
        }
        addLabeledComponent(associatedObjectLabel, moduleField)
      }
      addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      addComponent(installPackagesCheckBox)
      addLabeledComponent(PyBundle.message("python.sdk.poetry.executable"), poetryPathField)
    }
    add(builder.panel, BorderLayout.NORTH)
    update()
  }

  override fun getOrCreateSdk(): Sdk? {
    PropertiesComponent.getInstance().poetryPath = poetryPathField.text.nullize()
    return pyModalBlocking {
      val moduleBasePath = selectedModule?.basePath?.let { Path.of(it) }
                           ?: error("select module base path is invalid: ${selectedModule?.basePath}")
      val basePythonBinaryPath = baseSdkField.selectedSdk.homePath?.let { Path.of(it) }

      createNewPoetrySdk(moduleBasePath, existingSdks, basePythonBinaryPath, installPackagesCheckBox.isSelected).onSuccess {
        PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseSdkField.selectedSdk.homePath
      }
    }.getOrNull()
  }

  override fun getStatisticInfo(): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(type = InterpreterType.POETRY,
                                     target = InterpreterTarget.LOCAL,
                                     globalSitePackage = false,
                                     makeAvailableToAllProjects = false,
                                     previouslyConfigured = false)
  }

  override fun validateAll(): List<ValidationInfo> =
    emptyList() // Pre-target validation is not supported

  override fun addChangeListener(listener: Runnable) {
    poetryPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
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

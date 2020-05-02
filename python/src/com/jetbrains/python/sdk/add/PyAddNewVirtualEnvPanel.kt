// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.*
import icons.PythonIcons
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.event.DocumentEvent

/**
 * @author vlan
 */
class PyAddNewVirtualEnvPanel(private val project: Project?,
                              private val module: Module?,
                              private val existingSdks: List<Sdk>,
                              newProjectPath: String?,
                              private val context: UserDataHolder) : PyAddNewEnvPanel() {
  override val envName: String = "Virtualenv"

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      pathField.text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    }

  val path: String
    get() = pathField.text.trim()

  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.new.environment")
  override val icon: Icon = PythonIcons.Python.Virtualenv
  private val baseSdkField = PySdkPathChoosingComboBox()
  private val pathField = TextFieldWithBrowseButton().apply {
    text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    addBrowseFolderListener(PyBundle.message("python.sdk.select.location.for.virtualenv.title"), null, project,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val inheritSitePackagesField = JBCheckBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"))
  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  init {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("sdk.create.venv.dialog.label.location"), pathField)
      .addLabeledComponent(PyBundle.message("base.interpreter"), baseSdkField)
      .addComponent(inheritSitePackagesField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
    addBaseInterpretersAsync(baseSdkField, existingSdks, module, context)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validateEnvironmentDirectoryLocation(pathField),
                  validateSdkComboBox(baseSdkField, this))

  override fun getOrCreateSdk(): Sdk? {
    val root = pathField.text
    val baseSdk = baseSdkField.selectedSdk
      .let { if (it is PySdkToInstall) it.install(module) { detectSystemWideSdks(module, existingSdks, context) } else it }
    if (baseSdk == null) return null

    val task = object : Task.WithResult<String, ExecutionException>(project, PyBundle.message("python.sdk.creating.virtualenv.title"), false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        val packageManager = PyPackageManager.getInstance(baseSdk)
        return packageManager.createVirtualEnv(root, inheritSitePackagesField.isSelected)
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) projectBasePath else null
    val sdk = createSdkByGenerateTask(task, existingSdks, baseSdk, associatedPath, null) ?: return null
    if (!shared) {
      sdk.associateWithModule(module, newProjectPath)
    }
    moduleToExcludeSdkFrom(root, project)?.excludeInnerVirtualEnv(sdk)
    with(PySdkSettings.instance) {
      setPreferredVirtualEnvBasePath(FileUtil.toSystemIndependentName(pathField.text), projectBasePath)
      preferredVirtualEnvBaseSdk = baseSdk.homePath
    }
    return sdk
  }

  override fun addChangeListener(listener: Runnable) {
    pathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener.run()
      }
    })
    baseSdkField.childComponent.addItemListener { listener.run() }
  }

  private fun moduleToExcludeSdkFrom(path: String, project: Project?): Module? {
    val possibleProjects = if (project != null) listOf(project) else ProjectManager.getInstance().openProjects.asList()
    val rootFile = StandardFileSystems.local().refreshAndFindFileByPath(path) ?: return null
    return possibleProjects
      .asSequence()
      .map { ModuleUtil.findModuleForFile(rootFile, it) }
      .filterNotNull()
      .firstOrNull()
  }

  private val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: module?.basePath ?: project?.basePath
}

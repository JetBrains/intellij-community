/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.add

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.*
import icons.PythonIcons
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import java.io.File
import javax.swing.Icon
import javax.swing.event.DocumentEvent

/**
 * @author vlan
 */
class PyAddNewVirtualEnvPanel(private val project: Project?,
                              private val existingSdks: List<Sdk>,
                              newProjectPath: String?) : PyAddNewEnvPanel() {
  override val envName = "Virtualenv"

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      pathField.text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    }

  val path: String
    get() = pathField.text.trim()

  override val panelName = "New environment"
  override val icon: Icon = PythonIcons.Python.Virtualenv
  private val baseSdkField = PySdkPathChoosingComboBox(findBaseSdks(existingSdks), null).apply {
    val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk
    val detectedPreferredSdk = items.find { it.homePath == preferredSdkPath }
    selectedSdk = when {
      detectedPreferredSdk != null -> detectedPreferredSdk
      preferredSdkPath != null -> PyDetectedSdk(preferredSdkPath).apply {
        childComponent.insertItemAt(this, 0)
      }
      else -> items.getOrNull(0)
    }
  }
  private val pathField = TextFieldWithBrowseButton().apply {
    val defaultBasePath = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    val parentPath = PathUtil.getParentPath(defaultBasePath)
    val fileName = PathUtil.getFileName(defaultBasePath)
    text = FileUtil.findSequentNonexistentFile(File(parentPath), fileName, "").path
    addBrowseFolderListener("Select Location for Virtual Environment", null, project,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val inheritSitePackagesField = JBCheckBox("Inherit global site-packages")
  private val makeSharedField = JBCheckBox("Make available to all projects")

  init {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Location:", pathField)
      .addLabeledComponent("Base interpreter:", baseSdkField)
      .addComponent(inheritSitePackagesField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll() =
    listOf(validateEmptyOrNonExistingDirectoryLocation(pathField),
           validateSdkComboBox(baseSdkField))
      .filterNotNull()

  override fun getOrCreateSdk(): Sdk? {
    val root = pathField.text
    val task = object : Task.WithResult<String, ExecutionException>(project, "Creating Virtual Environment", false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        val baseSdk = baseSdkField.selectedSdk ?: throw ExecutionException("No base interpreter selected")
        val packageManager = PyPackageManager.getInstance(baseSdk)
        return packageManager.createVirtualEnv(root, inheritSitePackagesField.isSelected)
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) newProjectPath ?: project?.basePath else null
    val sdk = createSdkByGenerateTask(task, existingSdks, baseSdkField.selectedSdk, associatedPath) ?: return null
    if (!shared) {
      sdk.associateWithProject(project, newProjectPath != null)
    }
    excludeDirectoryFromProject(root, project)
    with(PySdkSettings.instance) {
      setPreferredVirtualEnvBasePath(FileUtil.toSystemIndependentName(pathField.text), projectBasePath)
      preferredVirtualEnvBaseSdk = baseSdkField.selectedSdk?.homePath
    }
    return sdk
  }

  override fun addChangeListener(listener: Runnable) {
    pathField.textField.document.addDocumentListener(object: DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        listener.run()
      }
    })
    baseSdkField.childComponent.addItemListener { listener.run() }
  }

  private fun excludeDirectoryFromProject(path: String, project: Project?) {
    val possibleProjects = if (project != null) listOf(project) else ProjectManager.getInstance().openProjects.asList()
    val rootFile = StandardFileSystems.local().refreshAndFindFileByPath(path) ?: return
    val module = possibleProjects
                   .asSequence()
                   .map { ModuleUtil.findModuleForFile(rootFile, it) }
                   .filterNotNull()
                   .firstOrNull() ?: return
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val contentEntry = model.contentEntries.firstOrNull {
      val contentFile = it.file
      contentFile != null && VfsUtil.isAncestor(contentFile, rootFile, true)
    } ?: return
    contentEntry.addExcludeFolder(rootFile)
    WriteAction.run<Throwable> {
      model.commit()
    }
  }

  private val projectBasePath: @SystemIndependent String
    get() = newProjectPath ?: project?.basePath ?: userHome

  private val userHome: @SystemIndependent String
    get() = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
}

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

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.execution.ExecutionException
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
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
import com.jetbrains.python.packaging.PyPackageService
import com.jetbrains.python.sdk.associateWithProject
import com.jetbrains.python.sdk.createSdkByGenerateTask
import com.jetbrains.python.sdk.findBaseSdks
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import icons.PythonIcons
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.awt.BorderLayout
import java.io.File
import javax.swing.Icon
import javax.swing.event.DocumentEvent

/**
 * @author vlan
 */
class PyAddNewVirtualEnvPanel(private val project: Project?,
                              private val existingSdks: List<Sdk>,
                              newProjectPath: String?) : PyAddSdkPanel() {
  companion object {
    private const val VIRTUALENV_ROOT_DIR_MACRO_NAME = "VIRTUALENV_ROOT_DIR"
  }

  var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      pathField.text = defaultBasePath
    }

  val path: String
    get() = pathField.text.trim()

  override val panelName = "New virtual environment"
  override val icon: Icon = PythonIcons.Python.Virtualenv
  private val baseSdkField = PySdkPathChoosingComboBox(findBaseSdks(existingSdks), null)
  private val pathField = TextFieldWithBrowseButton().apply {
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
      .addLabeledComponent("Base Interpreter:", baseSdkField)
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
    defaultBasePath = pathField.text
    return sdk
  }

  fun addChangeListener(listener: Runnable) {
    pathField.textField.document.addDocumentListener(object: DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        listener.run()
      }
    })
    baseSdkField.childComponent.addItemListener { listener.run() }
  }

  private fun excludeDirectoryFromProject(path: String, project: Project?) {
    val currentProject = project ?: findProjectFromFocus() ?: return
    val rootFile = StandardFileSystems.local().refreshAndFindFileByPath(path) ?: return
    val module = ModuleUtil.findModuleForFile(rootFile, currentProject) ?: return
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

  private fun findProjectFromFocus(): Project? =
    CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContextFromFocus.resultSync)

  private var defaultBasePath: @SystemDependent String
    get() {
      val pathMap = ExpandMacroToPathMap().apply {
        addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectBasePath)
        addMacroExpand(VIRTUALENV_ROOT_DIR_MACRO_NAME, defaultVirtualEnvRoot)
      }
      val defaultPath = when {
        defaultVirtualEnvRoot != userHome -> defaultVirtualEnvRoot
        else -> "$${PathMacroUtil.PROJECT_DIR_MACRO_NAME}$/venv"
      }
      val rawSavedPath = PyPackageService.getInstance().getVirtualEnvBasePath() ?: defaultPath
      val savedPath = pathMap.substitute(rawSavedPath, true)
      val path = when {
        FileUtil.isAncestor(projectBasePath, savedPath, true) -> savedPath
        else -> "$savedPath/${PathUtil.getFileName(projectBasePath)}"
      }
      return FileUtil.toSystemDependentName(path)
    }
    set(value) {
      val path = FileUtil.toSystemIndependentName(value)
      val pathMap = ReplacePathToMacroMap().apply {
        addMacroReplacement(projectBasePath, PathMacroUtil.PROJECT_DIR_MACRO_NAME)
        addMacroReplacement(defaultVirtualEnvRoot, VIRTUALENV_ROOT_DIR_MACRO_NAME)
      }
      val pathToSave = when {
        FileUtil.isAncestor(projectBasePath, path, true) -> path.trimEnd { !it.isLetter() }
        else -> PathUtil.getParentPath(path)
      }
      val substituted = pathMap.substitute(pathToSave, true)
      PyPackageService.getInstance().setVirtualEnvBasePath(substituted)
    }

  private val defaultVirtualEnvRoot: @SystemIndependent String
    get() = VirtualEnvSdkFlavor.getDefaultLocation()?.path ?: userHome

  private val projectBasePath: @SystemIndependent String
    get() = newProjectPath ?: project?.basePath ?: userHome

  private val userHome: @SystemIndependent String
    get() = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
}

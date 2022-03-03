// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.configuration.PyProjectVirtualEnvConfiguration
import com.jetbrains.python.statistics.modules
import training.lang.AbstractLangSupport
import training.project.ProjectUtils
import training.project.ReadMeCreator
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.max

abstract class PythonBasedLangSupport : AbstractLangSupport() {
  override val readMeCreator = ReadMeCreator()

  override fun installAndOpenLearningProject(contentRoot: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    // if we open project with isProjectCreatedFromWizard flag as true, PythonSdkConfigurator will not run and configure our sdks
    // and we will configure it individually without any race conditions
    val openProjectTask = OpenProjectTask(projectToClose = projectToClose, isProjectCreatedWithWizard = true)
    ProjectUtils.simpleInstallAndOpenLearningProject(contentRoot, this, openProjectTask, postInitCallback)
  }

  override fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk? {
    if (selectedSdk != null) {
      val module = project.modules.first()
      val existingSdks = getExistingSdks()
      return applyBaseSdk(project, selectedSdk, existingSdks, module)
    }
    if (project.pythonSdk != null) return null  // sdk already configured
    return createVenv(project)
  }

  private fun createVenv(project: Project): Sdk? {
    val module = project.modules.first()
    val existingSdks = getExistingSdks()
    val baseSdks = findBaseSdks(existingSdks, module, project)
    val preferredSdk = PyProjectVirtualEnvConfiguration.findPreferredVirtualEnvBaseSdk(baseSdks)
    return applyBaseSdk(project, preferredSdk, existingSdks, module)
  }

  private fun applyBaseSdk(project: Project,
                           preferredSdk: Sdk?,
                           existingSdks: List<Sdk>,
                           module: Module?): Sdk? {
    val venvRoot = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(project.basePath))
    val venvSdk = PyProjectVirtualEnvConfiguration.createVirtualEnvSynchronously(preferredSdk, existingSdks, venvRoot,
                                                                                 project.basePath, project, module, project)
    return venvSdk?.also {
      SdkConfigurationUtil.addSdk(it)
    }
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    setReadyToUseSdk(project, project.modules.first(), sdk)
  }

  private fun getExistingSdks(): List<Sdk> {
    return PyConfigurableInterpreterList.getInstance(null).allPythonSdks
      .sortedWith(PreferredSdkComparator.INSTANCE)
  }

  override fun checkSdk(sdk: Sdk?, project: Project) {
  }

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean {
    return file.name != projectSandboxRelativePath
  }

  override val projectSandboxRelativePath = "src/sandbox.py"

  override fun startFromWelcomeFrame(startCallback: (Sdk?) -> Unit) {
    val allExistingSdks = listOf(*PyConfigurableInterpreterList.getInstance(null).model.sdks)
    val existingSdks = ProjectSpecificSettingsStep.getValidPythonSdks(allExistingSdks)
    val context = UserDataHolderBase()
    val baseSdks = findBaseSdks(existingSdks, null, context)
    if (baseSdks.isEmpty()) {
      val baseSdkField = PySdkPathChoosingComboBox()

      val warningPlaceholder = JLabel()
      val formPanel = FormBuilder.createFormBuilder()
        .addComponent(warningPlaceholder)
        .addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
        .panel

      formPanel.preferredSize = Dimension(max(formPanel.preferredSize.width, 500), formPanel.preferredSize.height)
      val dialog = object : DialogWrapper(ProjectManager.getInstance().defaultProject) {
        override fun createCenterPanel(): JComponent = formPanel

        init {
          title = PyBundle.message("sdk.select.path")
          init()
        }
      }

      addBaseInterpretersAsync(baseSdkField, existingSdks, null, context) {
        val selectedSdk = baseSdkField.selectedSdk
        if (selectedSdk is PySdkToInstall) {
          val installationWarning = selectedSdk.getInstallationWarning(Messages.getOkButton())
          warningPlaceholder.text = "<html>$installationWarning</html>"
        }
        else {
          warningPlaceholder.text = ""
        }
      }

      dialog.title = PythonLessonsBundle.message("choose.python.sdk.to.start.learning.header")
      if (dialog.showAndGet()) {
        val selectedSdk = baseSdkField.selectedSdk
        if (selectedSdk == null) return
        startCallback(selectedSdk)
      }
    } else {
      startCallback(null)
    }
  }
}

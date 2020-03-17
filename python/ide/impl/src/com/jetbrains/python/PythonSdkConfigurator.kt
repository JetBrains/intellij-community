// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.AppUIUtil
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.pipenv.detectAndSetupPipEnv
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author vlan
 */
class PythonSdkConfigurator {
  companion object {
    private val BALLOON_NOTIFICATIONS = NotificationGroup("Python interpreter configuring", NotificationDisplayType.BALLOON, true)

    private fun findExistingAssociatedSdk(module: Module, existingSdks: List<Sdk>): Sdk? {
      return existingSdks
        .asSequence()
        .filter { it.sdkType is PythonSdkType && it.isAssociatedWithModule(module) }
        .sortedByDescending { it.homePath }
        .firstOrNull()
    }

    private fun findDetectedAssociatedEnvironment(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): PyDetectedSdk? {
      detectVirtualEnvs(module, existingSdks, context).firstOrNull { it.isAssociatedWithModule(module) }?.let { return it }
      detectCondaEnvs(module, existingSdks, context).firstOrNull { it.isAssociatedWithModule(module) }?.let { return it }
      return null
    }

    private fun getDefaultProjectSdk(): Sdk? {
      return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
    }

    private fun findExistingSystemWideSdk(existingSdks: List<Sdk>) =
      existingSdks.filter { it.isSystemWide }.sortedWith(PreferredSdkComparator.INSTANCE).firstOrNull()

    private fun findDetectedSystemWideSdk(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder) =
      detectSystemWideSdks(module, existingSdks, context).firstOrNull()

    private fun <T> guardIndicator(indicator: ProgressIndicator, computable: () -> T): T {
      return ProgressManager.getInstance().runProcess(computable, SensitiveProgressWrapper(indicator))
    }

    private fun onEdt(project: Project, runnable: () -> Unit) = AppUIUtil.invokeOnEdt(Runnable { runnable() }, project.disposed)

    private fun notifyAboutConfiguredSdk(project: Project, module: Module, sdk: Sdk) {
      BALLOON_NOTIFICATIONS.createNotification(
        "${sdk.name} has been configured as the project interpreter",
        NotificationType.INFORMATION
      ).apply {
        val configureSdkAction = NotificationAction.createSimpleExpiring("Configure a Python Interpreter...") {
          PySdkPopupFactory.createAndShow(project, module)
        }

        addAction(configureSdkAction)
        notify(project)
      }
    }
  }

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        ProgressManager.getInstance().run(
          object : Task.Backgroundable(project, "Configuring a Python Interpreter", true) {
            override fun run(indicator: ProgressIndicator) = configureSdk(project, indicator)
          }
        )
      }
    })
  }

  private fun configureSdk(project: Project, indicator: ProgressIndicator) {
    indicator.isIndeterminate = true

    if (project.isDefault ||
        project.pythonSdk != null ||
        project.basePath.let { it != null && Files.exists(Paths.get(it, Project.DIRECTORY_STORE_FOLDER)) }) return

    val context = UserDataHolderBase()
    val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return
    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    if (indicator.isCanceled) return
    indicator.text = "Looking for the previously used interpreter"
    guardIndicator(indicator) { findExistingAssociatedSdk(module, existingSdks) }?.let {
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        module.excludeInnerVirtualEnv(it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return
    indicator.text = "Looking for a virtual environment related to the project"
    guardIndicator(indicator) { findDetectedAssociatedEnvironment(module, existingSdks, context) }?.let {
      val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return

      onEdt(project) {
        SdkConfigurationUtil.addSdk(newSdk)
        newSdk.associateWithModule(module, null)
        SdkConfigurationUtil.setDirectoryProjectSdk(project, newSdk)
        module.excludeInnerVirtualEnv(newSdk)
        notifyAboutConfiguredSdk(project, module, newSdk)
      }

      return
    }

    // TODO: Introduce an extension for configuring a project via a Python SDK provider
    if (indicator.isCanceled) return
    indicator.text = "Looking for a Pipfile"
    guardIndicator(indicator) { detectAndSetupPipEnv(project, module, existingSdks) }?.let {
      onEdt(project) {
        SdkConfigurationUtil.addSdk(it)
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return
    indicator.text = "Looking for the default interpreter setting for a new project"
    guardIndicator(indicator) { getDefaultProjectSdk() }?.let {
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return
    indicator.text = "Looking for the previously used system-wide interpreter"
    guardIndicator(indicator) { findExistingSystemWideSdk(existingSdks) }?.let {
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return
    indicator.text = "Looking for a system-wide interpreter"
    guardIndicator(indicator) { findDetectedSystemWideSdk(module, existingSdks, context) }?.let {
      onEdt(project) {
        SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
          SdkConfigurationUtil.setDirectoryProjectSdk(project, this)
          notifyAboutConfiguredSdk(project, module, this)
        }
      }
    }
  }
}

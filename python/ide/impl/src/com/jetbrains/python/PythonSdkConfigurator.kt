// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.pipenv.detectAndSetupPipEnv

/**
 * @author vlan
 */
internal class PythonSdkConfigurator : DirectoryProjectConfigurator {
  companion object {
    private val BALLOON_NOTIFICATIONS = NotificationGroup("Python interpreter configuring", NotificationDisplayType.BALLOON, true)
    private val LOGGER = Logger.getInstance(PythonSdkConfigurator::class.java)

    private fun getDefaultProjectSdk(): Sdk? {
      return ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk?.takeIf { it.sdkType is PythonSdkType }
    }

    private fun findExistingSystemWideSdk(existingSdks: List<Sdk>) =
      filterSystemWideSdks(existingSdks).sortedWith(PreferredSdkComparator.INSTANCE).firstOrNull()

    private fun findDetectedSystemWideSdk(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder) =
      detectSystemWideSdks(module, existingSdks, context).firstOrNull()

    private fun <T> guardIndicator(indicator: ProgressIndicator, computable: () -> T): T {
      return ProgressManager.getInstance().runProcess(computable, SensitiveProgressWrapper(indicator))
    }

    private fun onEdt(project: Project, runnable: () -> Unit) = AppUIExecutor.onUiThread().expireWith(project).submit { runnable() }

    private fun notifyAboutConfiguredSdk(project: Project, module: Module, sdk: Sdk) {
      BALLOON_NOTIFICATIONS.createNotification(
        PyBundle.message("sdk.has.been.configured.as.the.project.interpreter", sdk.name),
        NotificationType.INFORMATION
      ).apply {
        val configureSdkAction = NotificationAction.createSimpleExpiring(PyBundle.message("configure.python.interpreter")) {
          PySdkPopupFactory.createAndShow(project, module)
        }

        addAction(configureSdkAction)
        notify(project)
      }
    }
  }

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    val sdk = project.pythonSdk
    LOGGER.debug { "Input: $sdk, $isProjectCreatedWithWizard" }
    if (sdk != null || isProjectCreatedWithWizard) {
      return
    }

    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      ProgressManager.getInstance().run(
        object : Task.Backgroundable(project, PyBundle.message("configuring.python.interpreter"), true) {
          override fun run(indicator: ProgressIndicator) = configureSdk(project, indicator)
        }
      )
    }
  }

  private fun configureSdk(project: Project, indicator: ProgressIndicator) {
    indicator.isIndeterminate = true

    val context = UserDataHolderBase()
    val module = ModuleManager.getInstance(project).modules.firstOrNull().also { LOGGER.debug { "Module: $it" } } ?: return
    val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is PythonSdkType }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.previous.interpreter")
    LOGGER.debug("Looking for the previously used interpreter")
    guardIndicator(indicator) { findExistingAssociatedSdk(module, existingSdks) }?.let {
      LOGGER.debug { "The previously used interpreter: $it" }
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        module.excludeInnerVirtualEnv(it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.related.venv")
    LOGGER.debug("Looking for a virtual environment related to the project")
    guardIndicator(indicator) { findDetectedAssociatedEnvironment(module, existingSdks, context) }?.let {
      LOGGER.debug { "Detected virtual environment related to the project: $it" }
      val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
      LOGGER.debug { "Created virtual environment related to the project: $newSdk" }

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

    indicator.text = PyBundle.message("looking.for.pipfile")
    LOGGER.debug("Looking for a Pipfile")
    guardIndicator(indicator) { detectAndSetupPipEnv(project, module, existingSdks) }?.let {
      LOGGER.debug { "Pipenv: $it" }
      onEdt(project) {
        SdkConfigurationUtil.addSdk(it)
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return

    if (PyCondaSdkCustomizer.instance.suggestSharedCondaEnvironments) {
      indicator.text = PyBundle.message("looking.for.shared.conda.environment")
      guardIndicator(indicator) {
        existingSdks
          .asSequence()
          .filter { it.sdkType is PythonSdkType && PythonSdkUtil.isConda(it) && !it.isAssociatedWithAnotherModule(module) }
          .firstOrNull()
      }?.let {
        onEdt(project) {
          SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
          notifyAboutConfiguredSdk(project, module, it)
        }
        return
      }

      guardIndicator(indicator) { detectCondaEnvs(module, existingSdks, context).firstOrNull() }?.let {
        val newSdk = it.setupAssociated(existingSdks, module.basePath) ?: return
        onEdt(project) {
          SdkConfigurationUtil.addSdk(newSdk)
          SdkConfigurationUtil.setDirectoryProjectSdk(project, newSdk)
          notifyAboutConfiguredSdk(project, module, newSdk)
        }
        return
      }

      if (indicator.isCanceled) return
    }

    indicator.text = PyBundle.message("looking.for.default.interpreter")
    LOGGER.debug("Looking for the default interpreter setting for a new project")
    guardIndicator(indicator) { getDefaultProjectSdk() }?.let {
      LOGGER.debug { "Default interpreter setting for a new project: $it" }
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.previous.system.interpreter")
    LOGGER.debug("Looking for the previously used system-wide interpreter")
    guardIndicator(indicator) { findExistingSystemWideSdk(existingSdks) }?.let {
      LOGGER.debug { "Previously used system-wide interpreter: $it" }
      onEdt(project) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
        notifyAboutConfiguredSdk(project, module, it)
      }
      return
    }

    if (indicator.isCanceled) return

    indicator.text = PyBundle.message("looking.for.system.interpreter")
    LOGGER.debug("Looking for a system-wide interpreter")
    guardIndicator(indicator) { findDetectedSystemWideSdk(module, existingSdks, context) }?.let {
      LOGGER.debug { "Detected system-wide interpreter: $it" }
      onEdt(project) {
        SdkConfigurationUtil.createAndAddSDK(it.homePath!!, PythonSdkType.getInstance())?.apply {
          LOGGER.debug { "Created system-wide interpreter: $this" }
          SdkConfigurationUtil.setDirectoryProjectSdk(project, this)
          notifyAboutConfiguredSdk(project, module, this)
        }
      }
    }
  }

}

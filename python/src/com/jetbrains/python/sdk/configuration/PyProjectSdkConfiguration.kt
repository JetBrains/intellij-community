// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.ui.EditorNotifications
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configuration.suppressors.PyPackageRequirementsInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.TipOfTheDaySuppressor
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.installExecutableViaPythonScript
import com.jetbrains.python.statistics.ConfiguredPythonInterpreterIdsHolder.Companion.SDK_HAS_BEEN_CONFIGURED_AS_THE_PROJECT_INTERPRETER
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

object PyProjectSdkConfiguration {
  fun configureSdkUsingCreateSdkInfo(module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool) {
    val lifetime = suppressTipAndInspectionsFor(module, createSdkInfoWithTool.toolId.id)

    val project = module.project
    project.service<SdkConfigurationService>().scope.launch {
      withBackgroundProgress(project, createSdkInfoWithTool.createSdkInfo.intentionName, false) {
        lifetime.use { setSdkUsingCreateSdkInfo(module, createSdkInfoWithTool) }
      }
    }
  }

  fun installToolForInspection(module: Module, createSdkInfo: CreateSdkInfo.WillInstallTool, toolId: ToolId) {
    val lifetime = suppressTipAndInspectionsFor(module, toolId.id)

    val project = module.project
    project.service<SdkConfigurationService>().scope.launch {
      withBackgroundProgress(project, createSdkInfo.intentionName, false) {
        lifetime.use { installToolAndShowErrorIfNeeded(module, createSdkInfo.pathPersister, createSdkInfo.toolToInstall) }
      }

      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }

  private suspend fun installToolAndShowErrorIfNeeded(module: Module, pathPersister: (Path) -> Unit, toolToInstall: String) {
    performToolInstallation(pathPersister, toolToInstall).errorOrNull?.also {
      ShowingMessageErrorSync.emit(it, module.project)
    }
  }

  private suspend fun performToolInstallation(pathPersister: (Path) -> Unit, toolToInstall: String): PyResult<Unit> {
    val systemPython = SystemPythonService().findSystemPythons().firstOrNull()
                       ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.python"))
    return installExecutableViaPythonScript(systemPython.asExecutablePython.binary, "-n", toolToInstall).mapSuccess(pathPersister)
  }

  suspend fun setSdkUsingCreateSdkInfo(
    module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool,
  ): Boolean = withContext(Dispatchers.Default) {
    thisLogger().debug("Configuring sdk using ${createSdkInfoWithTool.toolId}")

    val sdk = createSdkInfoWithTool.createSdkInfo.getSdkCreator(module).createSdk().getOr {
      ShowingMessageErrorSync.emit(it.error, module.project)
      return@withContext true
    }

    setReadyToUseSdk(module.project, module, sdk)
    thisLogger().debug("Successfully configured sdk using ${createSdkInfoWithTool.toolId}")
    true
  }

  suspend fun setReadyToUseSdk(project: Project, module: Module, sdk: Sdk) {
    if (module.isDisposed) {
      return
    }

    configurePythonSdk(project, module, sdk)
    withContext(Dispatchers.EDT) {
      notifyAboutConfiguredSdk(project, module, sdk)
    }
  }

  fun suppressTipAndInspectionsFor(module: Module, toolName: String): Disposable {
    val project = module.project

    val lifetime = Disposer.newDisposable(
      PythonPluginDisposable.getInstance(project),
      "Configuring sdk using $toolName"
    )

    TipOfTheDaySuppressor.suppress()?.let { Disposer.register(lifetime, it) }
    Disposer.register(lifetime, PyPackageRequirementsInspectionSuppressor(module))

    PythonSdkCreationWaiter.register(module, lifetime)
    return lifetime
  }

  private fun notifyAboutConfiguredSdk(project: Project, module: Module, sdk: Sdk) {
    if (isNotificationSilentMode(project) || WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) return
    NotificationGroupManager.getInstance().getNotificationGroup("ConfiguredPythonInterpreter")
      .createNotification(
        content = PyBundle.message("sdk.has.been.configured.as.the.project.interpreter", sdk.name),
        type = NotificationType.INFORMATION
      )
      .setDisplayId(SDK_HAS_BEEN_CONFIGURED_AS_THE_PROJECT_INTERPRETER)
      .apply {
        val configureSdkAction = NotificationAction.createSimpleExpiring(PySdkBundle.message("python.configure.interpreter.action")) {
          PySdkPopupFactory.createAndShow(module)
        }

        addAction(configureSdkAction)
        notify(project)
      }
  }
}

@Service(Service.Level.PROJECT)
private class SdkConfigurationService(val scope: CoroutineScope)

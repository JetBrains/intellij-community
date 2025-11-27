// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configuration.suppressors.PyInterpreterInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.PyPackageRequirementsInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.TipOfTheDaySuppressor
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.statistics.ConfiguredPythonInterpreterIdsHolder.Companion.SDK_HAS_BEEN_CONFIGURED_AS_THE_PROJECT_INTERPRETER
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PyProjectSdkConfiguration {
  fun configureSdkUsingCreateSdkInfo(module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool) {
    val lifetime = suppressTipAndInspectionsFor(module, createSdkInfoWithTool.toolId.id)

    val project = module.project
    PyPackageCoroutine.launch(project) {
      withBackgroundProgress(project, createSdkInfoWithTool.createSdkInfo.intentionName, false) {
        lifetime.use { setSdkUsingCreateSdkInfo(module, createSdkInfoWithTool, false) }
      }
    }
  }

  suspend fun setSdkUsingCreateSdkInfo(
    module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool, needsConfirmation: NeedsConfirmation,
  ): Boolean = withContext(Dispatchers.Default) {
    thisLogger().debug("Configuring sdk using ${createSdkInfoWithTool.toolId}")

    val sdk = createSdkInfoWithTool.createSdkInfo.sdkCreator(needsConfirmation).getOr {
      ShowingMessageErrorSync.emit(it.error, module.project)
      return@withContext true
    } ?: return@withContext false

    setReadyToUseSdk(module.project, module, sdk)
    thisLogger().debug("Successfully configured sdk using ${createSdkInfoWithTool.toolId}")
    true
  }

  fun setReadyToUseSdkSync(project: Project, module: Module, sdk: Sdk) {
    runBlockingMaybeCancellable {
      setReadyToUseSdk(project, module, sdk)
    }
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
    PyInterpreterInspectionSuppressor.suppress(project)?.let { Disposer.register(lifetime, it) }
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

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.projectModel.uv.UvProjectModelService
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configuration.suppressors.PyInterpreterInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.PyPackageRequirementsInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.TipOfTheDaySuppressor
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.uv.isUv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PyProjectSdkConfiguration {
  fun configureSdkUsingExtension(module: Module, extension: PyProjectSdkConfigurationExtension) {
    val lifetime = suppressTipAndInspectionsFor(module, extension)

    val project = module.project
    PyPackageCoroutine.launch(project) {
      val title = extension.getIntention(module) ?: PySdkBundle.message("python.configuring.interpreter.progress")
      withBackgroundProgress(project, title, false) {
        lifetime.use {
          setSdkUsingExtension(module, extension) {
            withContext(Dispatchers.Default) {
              extension.createAndAddSdkForInspection(module)
            }
          }
        }
      }
    }
  }

  suspend fun setSdkUsingExtension(module: Module, extension: PyProjectSdkConfigurationExtension, supplier: suspend () -> Sdk?): Boolean {
    ProgressManager.progress("")
    PyProjectSdkConfiguration.thisLogger().debug("Configuring sdk with ${extension.javaClass.canonicalName} extension")

    val sdk = supplier() ?: return false
    // TODO Move this to PyUvSdkConfiguration, show better notification
    if (sdk.isUv && Registry.`is`("python.project.model.uv", false)) {
      val ws = UvProjectModelService.findWorkspace(module)
      if (ws != null) {
        for (wsModule in ws.members + ws.root) {
          setReadyToUseSdk(wsModule.project, wsModule, sdk)
        }
        return true
      }
    }
    setReadyToUseSdk(module.project, module, sdk)
    return true
  }

  fun setReadyToUseSdkSync(project: Project, module: Module, sdk: Sdk) {
    runBlockingMaybeCancellable {
      setReadyToUseSdk(project, module, sdk)
    }
  }

  suspend fun setReadyToUseSdk(project: Project, module: Module, sdk: Sdk) {
    withContext(Dispatchers.EDT) {
      if (module.isDisposed) {
        return@withContext
      }

      configurePythonSdk(project, module, sdk)
      notifyAboutConfiguredSdk(project, module, sdk)
    }
  }

  fun suppressTipAndInspectionsFor(module: Module, extension: PyProjectSdkConfigurationExtension): Disposable {
    val project = module.project

    val lifetime = Disposer.newDisposable(
      PythonPluginDisposable.getInstance(project),
      "Configuring sdk using ${extension.javaClass.name} extension"
    )

    TipOfTheDaySuppressor.suppress()?.let { Disposer.register(lifetime, it) }
    PyInterpreterInspectionSuppressor.suppress(project)?.let { Disposer.register(lifetime, it) }
    Disposer.register(lifetime, PyPackageRequirementsInspectionSuppressor(module))

    PythonSdkCreationWaiter.register(module, lifetime)
    return lifetime
  }

  private fun notifyAboutConfiguredSdk(project: Project, module: Module, sdk: Sdk) {
    if (isNotificationSilentMode(project)) return
    NotificationGroupManager.getInstance().getNotificationGroup("ConfiguredPythonInterpreter").createNotification(
      PyBundle.message("sdk.has.been.configured.as.the.project.interpreter", sdk.name),
      NotificationType.INFORMATION
    ).apply {
      val configureSdkAction = NotificationAction.createSimpleExpiring(PySdkBundle.message("python.configure.interpreter.action")) {
        PySdkPopupFactory.createAndShow(module)
      }

      addAction(configureSdkAction)
      notify(project)
    }
  }
}
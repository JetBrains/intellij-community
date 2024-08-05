// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.ide.GeneralSettings
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.ui.PyUiUtil

object PyProjectSdkConfiguration {

  private val LOGGER = Logger.getInstance(PyProjectSdkConfiguration::class.java)

  fun configureSdkUsingExtension(module: Module, extension: PyProjectSdkConfigurationExtension, supplier: () -> Sdk?) {
    val lifetime = suppressTipAndInspectionsFor(module, extension)

    ProgressManager.getInstance().run(
      object : Backgroundable(module.project, PySdkBundle.message("python.configuring.interpreter.progress"), false) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          lifetime.use { setSdkUsingExtension(module, extension, supplier) }
        }
      }
    )
  }

  @RequiresBackgroundThread
  fun setSdkUsingExtension(module: Module, extension: PyProjectSdkConfigurationExtension, supplier: () -> Sdk?) {
    ProgressManager.progress("")
    LOGGER.debug("Configuring sdk with ${extension.javaClass.canonicalName} extension")

    supplier()?.let { setReadyToUseSdk(module.project, module, it) }
  }

  fun setReadyToUseSdk(project: Project, module: Module, sdk: Sdk) {
    runInEdt {
      if (module.isDisposed) {
        return@runInEdt
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

private class TipOfTheDaySuppressor private constructor() : Disposable {

  private val savedValue: Boolean

  companion object {
    private val LOGGER = Logger.getInstance(TipOfTheDaySuppressor::class.java)

    fun suppress(): Disposable? {
      return if (!GeneralSettings.getInstance().isShowTipsOnStartup) null else TipOfTheDaySuppressor()
    }
  }

  init {
    val settings = GeneralSettings.getInstance()

    savedValue = settings.isShowTipsOnStartup
    settings.isShowTipsOnStartup = false
    LOGGER.info("Tip of the day has been disabled")
  }

  override fun dispose() {
    val settings = GeneralSettings.getInstance()

    if (!settings.isShowTipsOnStartup) { // nothing has been changed between init and dispose
      settings.isShowTipsOnStartup = savedValue
      LOGGER.info("Tip of the day has been enabled")
    }
    else {
      LOGGER.info("Tip of the day was enabled somewhere else")
    }
  }
}

private class PyInterpreterInspectionSuppressor : PyInspectionExtension() {

  companion object {
    private val LOGGER = Logger.getInstance(PyInterpreterInspectionSuppressor::class.java)
    private var suppress = false

    fun suppress(project: Project): Disposable? {
      PyUiUtil.clearFileLevelInspectionResults(project)
      return if (suppress) null else Suppressor()
    }
  }

  override fun ignoreInterpreterWarnings(file: PyFile): Boolean = suppress

  private class Suppressor : Disposable {

    init {
      suppress = true
      LOGGER.info("Interpreter warnings have been disabled")
    }

    override fun dispose() {
      suppress = false
      LOGGER.info("Interpreter warnings have been enabled")
    }
  }
}

private class PyPackageRequirementsInspectionSuppressor(module: Module): Disposable {

  private val listener = PyPackageRequirementsInspection.RunningPackagingTasksListener(module)

  init {
    listener.started()
  }

  override fun dispose() {
    listener.finished(emptyList())
  }
}
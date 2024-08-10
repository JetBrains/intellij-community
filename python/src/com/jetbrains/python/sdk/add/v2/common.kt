// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.pipenv.PIPENV_ICON
import com.jetbrains.python.sdk.poetry.POETRY_ICON
import com.jetbrains.python.statistics.InterpreterTarget
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon


@Service(Service.Level.APP)
class PythonAddSdkService(val coroutineScope: CoroutineScope)

abstract class PythonAddEnvironment(open val model: PythonAddInterpreterModel) {

  val state: AddInterpreterState
    get() = model.state

  internal val propertyGraph
    get() = model.propertyGraph

  abstract fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor)
  open fun onShown() {}

  /**
   * Returns created SDK ready to use
   */
  @RequiresEdt
  abstract fun getOrCreateSdk(): Sdk
  abstract fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo
}

abstract class PythonNewEnvironmentCreator(override val model: PythonMutableTargetAddInterpreterModel) : PythonAddEnvironment(model)
abstract class PythonExistingEnvironmentConfigurator(model: PythonAddInterpreterModel) : PythonAddEnvironment(model)





enum class PythonSupportedEnvironmentManagers(val nameKey: String, val icon: Icon) {
  VIRTUALENV("sdk.create.custom.virtualenv", PythonIcons.Python.Virtualenv),
  CONDA("sdk.create.custom.conda", PythonIcons.Python.Anaconda),
  POETRY("sdk.create.custom.poetry", POETRY_ICON),
  PIPENV("sdk.create.custom.pipenv", PIPENV_ICON),
  PYTHON("sdk.create.custom.python", com.jetbrains.python.psi.icons.PythonPsiApiIcons.Python)
}

enum class PythonInterpreterSelectionMode(val nameKey: String) {
  PROJECT_VENV("sdk.create.type.project.venv"),
  BASE_CONDA("sdk.create.type.base.conda"),
  CUSTOM("sdk.create.type.custom")
}

enum class PythonInterpreterCreationTargets(val nameKey: String, val icon: Icon) {
  LOCAL_MACHINE("sdk.create.targets.local", AllIcons.Nodes.HomeFolder),
  SSH("", AllIcons.Nodes.HomeFolder),
}

fun PythonInterpreterCreationTargets.toStatisticsField(): InterpreterTarget {
  return when (this) {
    PythonInterpreterCreationTargets.LOCAL_MACHINE -> InterpreterTarget.LOCAL
    else -> throw NotImplementedError("PythonInterpreterCreationTargets added, but not accounted for in statistics")
  }
}


enum class PythonInterpreterSelectionMethod {
  CREATE_NEW, SELECT_EXISTING
}

internal fun installBaseSdk(sdk: Sdk, existingSdks: List<Sdk>): Sdk? {
  val installed = installSdkIfNeeded(sdk, null, existingSdks).getOrLogException(LOGGER)
  if (installed == null) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("Python interpreter installation")
      .createNotification(message("python.sdk.installation.balloon.error.message"), NotificationType.ERROR)
    notification.collapseDirection

    notification.addAction(NotificationAction.createSimple(message("python.sdk.installation.balloon.error.action")) {
      notification.expire()
      HelpManager.getInstance().invokeHelp("create.python.interpreter")
    })

    NotificationsManager
      .getNotificationsManager()
      .showNotification(notification, IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project)
    return null
  }
  return installed
}


internal fun setupSdkIfDetected(interpreter: PythonSelectableInterpreter, existingSdks: List<Sdk>, targetConfig: TargetEnvironmentConfiguration? = null): Sdk? {
  if (interpreter is ExistingSelectableInterpreter) return interpreter.sdk

  val homeDir = interpreter.homePath.virtualFileOnTarget(targetConfig) ?: return null // todo handle
  val newSdk = SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(),
                                             homeDir,
                                             PythonSdkType.getInstance(),
                                             false,
                                             null, // todo create additional data for target
                                             null) ?: return null
  SdkConfigurationUtil.addSdk(newSdk)
  return newSdk
}
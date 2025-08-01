// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.pipenv.PIPENV_ICON
import com.jetbrains.python.sdk.poetry.POETRY_ICON
import com.jetbrains.python.sdk.uv.UV_ICON
import com.jetbrains.python.statistics.InterpreterTarget
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

abstract class PythonAddEnvironment(open val model: PythonAddInterpreterModel) {

  val state: AddInterpreterState
    get() = model.state

  internal val propertyGraph
    get() = model.propertyGraph

  abstract fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor)
  abstract fun onShown(scope: CoroutineScope)

  /**
   * Returns created SDK ready to use
   *
   * Error is shown to user. Do not catch all exceptions, only return exceptions valuable to user
   */
  protected abstract suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk>

  protected suspend fun setupSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val sdk = getOrCreateSdk(moduleOrProject).getOr { return it }

    moduleOrProject.moduleIfExists?.excludeInnerVirtualEnv(sdk)

    return Result.success(sdk)
  }

  @ApiStatus.Internal
  suspend fun getOrCreateSdkWithModal(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    return withModalProgress(ModalTaskOwner.guess(),
                             message("python.sdk.progress.setting.up.environment"),
                             TaskCancellation.cancellable()) {
      setupSdk(moduleOrProject)
    }
  }

  @ApiStatus.Internal
  suspend fun getOrCreateSdkWithBackground(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    return withBackgroundProgress(moduleOrProject.project,
                                  message("python.sdk.progress.setting.up.environment"),
                                  TaskCancellation.cancellable()) {
      setupSdk(moduleOrProject)
    }
  }

  open suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> = Result.success(Unit)

  abstract fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo
}

abstract class PythonNewEnvironmentCreator(override val model: PythonMutableTargetAddInterpreterModel) : PythonAddEnvironment(model) {
  internal val venvExistenceValidationState: AtomicProperty<VenvExistenceValidationState> =
    AtomicProperty(VenvExistenceValidationState.Invisible)

  internal fun Row.venvExistenceValidationAlert(validationRequestor: DialogValidationRequestor, onSelectExisting: () -> Unit) {
    venvExistenceValidationAlert(venvExistenceValidationState, onSelectExisting)
      .align(Align.FILL)
      .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(venvExistenceValidationState))
      .cellValidation { component ->
        addInputRule {
          if (!component.isVisible) {
            return@addInputRule null
          }

          val state = venvExistenceValidationState.get()

          if (state is VenvExistenceValidationState.Error)
            ValidationInfo("")
          else
            null
        }
      }
  }
}

abstract class PythonExistingEnvironmentConfigurator(model: PythonAddInterpreterModel) : PythonAddEnvironment(model)


enum class PythonSupportedEnvironmentManagers(val nameKey: String, val icon: Icon) {
  VIRTUALENV("sdk.create.custom.virtualenv", PythonIcons.Python.Virtualenv),
  CONDA("sdk.create.custom.conda", PythonIcons.Python.Anaconda),
  POETRY("sdk.create.custom.poetry", POETRY_ICON),
  PIPENV("sdk.create.custom.pipenv", PIPENV_ICON),
  UV("sdk.create.custom.uv", UV_ICON),
  HATCH("sdk.create.custom.hatch", PythonHatchIcons.Logo),
  PYTHON("sdk.create.custom.python", PythonPsiApiIcons.Python)
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

internal fun PythonInterpreterCreationTargets.toStatisticsField(): InterpreterTarget {
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


internal suspend fun setupSdkIfDetected(interpreter: PythonSelectableInterpreter, existingSdks: List<Sdk>, targetConfig: TargetEnvironmentConfiguration? = null): Sdk? {
  if (interpreter is ExistingSelectableInterpreter) return interpreter.sdk

  val homeDir = interpreter.homePath.virtualFileOnTarget(targetConfig) ?: return null // todo handle
  val newSdk = SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(),
                                             homeDir,
                                             PythonSdkType.getInstance(),
                                             false,
                                             null, // todo create additional data for target
                                             null) ?: return null
  newSdk.persist()
  return newSdk
}
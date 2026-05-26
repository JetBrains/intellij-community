// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.intellij.python.community.impl.pipenv.PIPENV_ICON
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.intellij.python.venv.icons.PythonVenvIcons
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.parser.icons.PythonParserIcons
import com.jetbrains.python.sdk.LOGGER
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.createSdkGuessingTypeByPath
import com.jetbrains.python.sdk.excludeInnerVirtualEnv
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.PythonInterpreterInstallationIdsHolder.Companion.PYTHON_INSTALLATION_INTERRUPTED
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon

abstract class PythonAddEnvironment<P : PathHolder>(open val model: PythonAddInterpreterModel<P>) {

  val state: AddInterpreterState<P>
    get() = model.state

  internal val propertyGraph
    get() = model.propertyGraph

  protected abstract val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?>?
  protected abstract val toolExecutablePersister: suspend (P) -> Unit

  abstract fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor)
  abstract fun onShown(scope: CoroutineScope)

  /**
   * Returns created SDK ready to use
   *
   * Error is shown to user. Do not catch all exceptions, only return exceptions valuable to user
   */
  protected abstract suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk>

  @ApiStatus.Internal
  suspend fun setupSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    savePathToExecutableToProperties(null)
    val sdk = getOrCreateSdk(moduleOrProject).getOr { return it }

    moduleOrProject.project.excludeInnerVirtualEnv(sdk)
    moduleOrProject.moduleIfExists?.let {
      it.pythonSdk = sdk
      sdk.setAssociationToModule(it)
    }

    return Result.success(sdk)
  }

  /**
   * Saves the provided path to an executable in the properties of the environment
   *
   * @param [pathHolder] The path holder of the path to the executable that needs to be saved. This may be null when we try to find the tool automatically.
   */
  protected suspend fun savePathToExecutableToProperties(pathHolder: P?) {
    val savingPath = pathHolder ?: toolExecutable?.get()?.pathHolder ?: return
    if (!model.fileSystem.isLocal) return
    toolExecutablePersister(savingPath)
  }

  open suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> = Result.success(Unit)

  abstract fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo
}

abstract class PythonNewEnvironmentCreator<P : PathHolder>(override val model: PythonMutableTargetAddInterpreterModel<P>) :
  PythonAddEnvironment<P>(model) {
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

abstract class PythonExistingEnvironmentConfigurator<P : PathHolder>(model: PythonAddInterpreterModel<P>) : PythonAddEnvironment<P>(model)


enum class PythonSupportedEnvironmentManagers(
  val toolId: ToolId,
  val nameKey: String,
  val icon: Icon,
  val sshAutoUploadRequired: Boolean,
  val isFSSupported: (FileSystem<*>) -> Boolean = { it.isLocal },
) {
  VIRTUALENV(VENV_TOOL_ID, "sdk.create.custom.virtualenv", PythonVenvIcons.VirtualEnv, sshAutoUploadRequired = false, { true }),
  CONDA(CONDA_TOOL_ID, "sdk.create.custom.conda", PythonCommunityImplCondaIcons.Anaconda, sshAutoUploadRequired = false, { true }),
  POETRY(POETRY_TOOL_ID, "sdk.create.custom.poetry", PythonCommunityImplPoetryCommonIcons.Poetry, sshAutoUploadRequired = false),
  PIPENV(PIPENV_TOOL_ID, "sdk.create.custom.pipenv", PIPENV_ICON, sshAutoUploadRequired = false),
  UV(UV_TOOL_ID, "sdk.create.custom.uv", PythonUvCommonIcons.UV, sshAutoUploadRequired = true, { true }),
  HATCH(HATCH_TOOL_ID, "sdk.create.custom.hatch", PythonHatchIcons.Logo, sshAutoUploadRequired = false),
  PYTHON(VENV_TOOL_ID, "sdk.create.custom.python", PythonParserIcons.PythonFile, sshAutoUploadRequired = false, { true })
}

enum class PythonInterpreterSelectionMode(val nameKey: String) {
  PROJECT_VENV("sdk.create.type.project.venv"),
  PROJECT_UV("sdk.create.type.project.uv"),
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

@RequiresEdt
internal fun installBaseSdk(installRequest: InstallablePythonSdk): Sdk? {
  val installed = installRequest.install(null) {
    PythonSdkUtil.getAllSdks()
  }.getOrLogException(LOGGER)

  if (installed == null) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("Python interpreter installation")
      .createNotification(message("python.sdk.installation.balloon.error.message"), NotificationType.ERROR)
      .setDisplayId(PYTHON_INSTALLATION_INTERRUPTED)
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


internal suspend fun <P : PathHolder> PythonSelectableInterpreter<P>.setupSdk(
  moduleOrProject: ModuleOrProject,
  fileSystem: FileSystem<P>,
  targetPanelExtension: TargetPanelExtension?,
  isAssociateWithModule: Boolean,
): PyResult<Sdk> {
  when (this) {
    is ExistingSelectableInterpreter -> return PyResult.success(sdkWrapper.sdk)
    is DetectedSelectableInterpreter, is InstallableSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> Unit
  }

   val homePath = this@setupSdk.homePath!!

  // Do our best to guess the flavor
  return createSdkGuessingTypeByPath(homePath, fileSystem, moduleOrProject, targetPanelExtension, isAssociateWithModule)
}



internal fun savePathForEelOnly(pathHolder: PathHolder, pathPersister: (Path) -> Unit) {
  when (pathHolder) {
    is PathHolder.Eel -> pathPersister(pathHolder.path)
    is PathHolder.Target -> Unit
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.parser.icons.PythonParserIcons
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.pipenv.PIPENV_ICON
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.PythonInterpreterInstallationIdsHolder.Companion.PYTHON_INSTALLATION_INTERRUPTED
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

abstract class PythonAddEnvironment<P : PathHolder>(open val model: PythonAddInterpreterModel<P>) {

  val state: AddInterpreterState<P>
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

    moduleOrProject.project.excludeInnerVirtualEnv(sdk)
    moduleOrProject.moduleIfExists?.let { sdk.setAssociationToModule(it) }

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

abstract class PythonNewEnvironmentCreator<P : PathHolder>(override val model: PythonMutableTargetAddInterpreterModel<P>) : PythonAddEnvironment<P>(model) {
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
  val isFSSupported: (FileSystem<*>) -> Boolean = { (it as? FileSystem.Eel)?.eelApi == localEel },
) {
  VIRTUALENV(VENV_TOOL_ID, "sdk.create.custom.virtualenv", PythonIcons.Python.Virtualenv, { true }),
  CONDA(CONDA_TOOL_ID, "sdk.create.custom.conda", PythonIcons.Python.Anaconda, { true }),
  POETRY(POETRY_TOOL_ID, "sdk.create.custom.poetry", PythonCommunityImplPoetryCommonIcons.Poetry),
  PIPENV(PIPENV_TOOL_ID, "sdk.create.custom.pipenv", PIPENV_ICON),
  UV(UV_TOOL_ID, "sdk.create.custom.uv", PythonCommunityImplUVCommonIcons.UV),
  HATCH(HATCH_TOOL_ID, "sdk.create.custom.hatch", PythonHatchIcons.Logo, { it is FileSystem.Eel }),
  PYTHON(VENV_TOOL_ID, "sdk.create.custom.python", PythonParserIcons.PythonFile, { true })
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

internal fun installBaseSdk(sdk: Sdk, existingSdks: List<Sdk>): Sdk? {
  val installed = installSdkIfNeeded(sdk, null, existingSdks).getOrLogException(LOGGER)
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


internal suspend fun <P : PathHolder> setupSdk(
  project: Project?,
  allSdks: List<Sdk>,
  fileSystem: FileSystem<P>,
  pythonBinaryPath: P,
  languageLevel: LanguageLevel,
  targetPanelExtension: TargetPanelExtension?,
): PyResult<Sdk> {
  val sdkType = PythonSdkType.getInstance()

  val (additionalData, customSdkSuggestedName) = when (fileSystem) {
    is FileSystem.Eel -> null to suggestAssociatedSdkName(pythonBinaryPath.toString(), project?.basePath)
    is FileSystem.Target -> {
      val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance())).also {
        it.interpreterPath = pythonBinaryPath.toString()
        it.targetEnvironmentConfiguration = fileSystem.targetEnvironmentConfiguration
      }
      targetPanelExtension?.let {
        it.applyToTargetConfiguration()
        it.applyToAdditionalData(data)
      }
      val name = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, data, languageLevel.toPythonVersion())
      data to name
    }
  }

  val sdk = SdkConfigurationUtil.createSdk(
    allSdks,
    pythonBinaryPath.toString(),
    sdkType,
    additionalData,
    customSdkSuggestedName
  )

  sdk.sdkModificator.let { modifiableSdk ->
    modifiableSdk.versionString = languageLevel.toPythonVersion()
    writeAction {
      modifiableSdk.commitChanges()
    }
  }

  sdkType.setupSdkPaths(sdk)
  return PyResult.success(sdk)
}

internal suspend fun <P : PathHolder> PythonSelectableInterpreter<P>.setupSdk(
  moduleOrProject: ModuleOrProject,
  allSdks: List<Sdk>,
  fileSystem: FileSystem<P>,
  targetPanelExtension: TargetPanelExtension?,
  isAssociateWithModule: Boolean,
): PyResult<Sdk> {
  if (this is ExistingSelectableInterpreter) {
    return PyResult.success(sdkWrapper.sdk)
  }

  val newSdk = setupSdk(
    project = moduleOrProject.project,
    allSdks = allSdks,
    fileSystem = fileSystem,
    pythonBinaryPath = homePath!!,
    languageLevel = pythonInfo.languageLevel,
    targetPanelExtension = targetPanelExtension
  ).getOr { return it }

  val module = PyProjectCreateHelpers.getModule(moduleOrProject, newSdk.homeDirectory)
  if (isAssociateWithModule && module != null) {
    newSdk.setAssociationToModule(module)
  }
  newSdk.persist()

  moduleOrProject.project.excludeInnerVirtualEnv(newSdk)

  return PyResult.success(newSdk)
}

class VersionFormatException : Exception()

data class Version(val value: String) {
  override fun toString(): String {
    return value
  }

  companion object {
    fun parse(versionString: String): Version {
      return Version(versionString)
    }
  }
}

internal suspend fun BinaryToExec.getToolVersion(toolVersionPrefix: String): PyResult<Version> {
  val version = withContext(Dispatchers.IO) {
    ExecService().execGetStdout(this@getToolVersion, Args("--version"))
  }.getOr { return it }

  val pattern = "^$toolVersionPrefix,?(?:\\s\\(?version)?\\s([^\\s)]+).*$".toRegex(RegexOption.IGNORE_CASE)
  val matchResult = pattern.matchEntire(version)
  return if (matchResult != null) {
    val (versionString) = matchResult.destructured
    try {
      PyResult.success(Version.parse(versionString))
    }
    catch (ex: VersionFormatException) {
      PyResult.localizedError(ex.localizedMessage)
    }
  }
  else {
    val versionPresentation = StringUtil.shortenTextWithEllipsis(version, 250, 0, true)
    PyResult.localizedError(message("selected.tool.is.wrong", toolVersionPrefix.trim(), versionPresentation))
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
internal abstract class CustomNewEnvironmentCreator<P : PathHolder>(
  private val name: String,
  model: PythonMutableTargetAddInterpreterModel<P>,
  protected val errorSink: ErrorSink,
) : PythonNewEnvironmentCreator<P>(model) {
  internal lateinit var basePythonComboBox: PythonInterpreterComboBox<P>
  internal lateinit var executablePath: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      basePythonComboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.custom.base.python"),
        selectedSdkProperty = model.state.baseInterpreter,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedSystemPython,
      )

      executablePath = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = toolValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", name),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", name),
        installAction = createInstallFix(errorSink),
      )

      row("") {
        venvExistenceValidationAlert(validationRequestor) {
          onVenvSelectExisting()
        }
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    executablePath.initialize(scope)
    basePythonComboBox.initialize(scope, model.baseInterpreters)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      is ModuleOrProject.ProjectOnly -> null
    }
    val moduleBasePath = module?.baseDir?.path?.let { Path.of(it) }
                         ?: model.projectPathFlows.projectPath.first()
                         ?: error("module base path can't be recognized, both module and project are nulls")

    val newSdk = setupEnvSdk(moduleBasePath).getOr { return it }

    newSdk.persist()
    if (module != null) {
      newSdk.setAssociationToModule(module)
      module.baseDir?.refresh(true, false)
    }


    return Result.success(newSdk)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo =
    InterpreterStatisticsInfo(
      type = interpreterType,
      target = target.toStatisticsField(),
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = false,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )

  /**
   * Creates an installation fix for an executable (poetry, pipenv, uv, hatch).
   *
   * 1. Checks if the installation of the fix requires an undownloaded env.
   * 2. If it doesn't, downloads the env and selects it.
   * 3. Checks if `pythonExecutable` has pip.
   * 4. If it doesn't, checks if pip is installed globally.
   * 5. If it isn't, downloads and installs pip from "https://bootstrap.pypa.io/get-pip.py".
   * 6. Runs `(pythonExecutable -m) pip install <package_name> --user`.
   * 7. Reruns `detectExecutable`.
   */
  @RequiresEdt
  protected fun createInstallFix(errorSink: ErrorSink): ActionLink {
    return ActionLink(message("sdk.create.custom.venv.install.fix.title", name, "via pip")) {
      PythonSdkFlavor.clearExecutablesCache()
      installExecutable(errorSink)
      runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.progress.title.detect.executable")) {
        toolValidator.autodetectExecutable()
      }
    }
  }

  /**
   * Downloads the selected downloadable env (if selected), then installs the necessary executable in the Python environment.
   *
   * Initiates a blocking modal progress task to:
   * 1. Ensure that the environment is downloaded (if selected).
   * 2. Ensure that pip is installed.
   * 3. Install the executable (specified by `name`) using either a custom installation script or via pip.
   */
  @RequiresEdt
  private fun installExecutable(errorSink: ErrorSink) {
    val baseInterpreter = model.state.baseInterpreter.get()

    val installedSdk = when (baseInterpreter) {
      is InstallableSelectableInterpreter -> installBaseSdk(baseInterpreter.sdk, model.existingSdks)
        ?.let {
          val sdkWrapper =
            runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.progress.title.detect.executable")) {
              model.fileSystem.wrapSdk(it)
            }
          val installed = model.addInstalledInterpreter(sdkWrapper.homePath, baseInterpreter.pythonInfo)
          model.state.baseInterpreter.set(installed)
          installed
        }
      is DetectedSelectableInterpreter, is ExistingSelectableInterpreter, is ManuallyAddedSelectableInterpreter, null -> null
    }

    // installedSdk is null when the selected sdk isn't downloadable
    // model.state.baseInterpreter could be null if no SDK was selected
    val pythonExecutablePath = installedSdk?.homePath ?: model.state.baseInterpreter.get()?.homePath
    val pythonExecutable = pythonExecutablePath?.let { model.fileSystem.getBinaryToExec(it) } ?: return

    runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.install.fix.title", name, "via pip")) {
      val versionArgs: List<String> = installationVersion?.let { listOf("-v", it) } ?: emptyList()
      when (val r = installExecutableViaPythonScript(pythonExecutable, "-n", name, *versionArgs.toTypedArray())) {
        is Result.Success -> {
          val pathHolder = PathHolder.Eel(r.result)
          savePathToExecutableToProperties(pathHolder as? P)
        }
        is Result.Failure -> {
          errorSink.emit(r.error)
        }
      }
    }
  }

  internal abstract val interpreterType: InterpreterType

  internal abstract val toolValidator: ToolValidator<P>

  internal open val installationVersion: String? = null

  protected abstract suspend fun setupEnvSdk(moduleBasePath: Path): PyResult<Sdk>

  internal open fun onVenvSelectExisting() {}
}

internal suspend fun <P : PathHolder> PythonMutableTargetAddInterpreterModel<P>.getOrInstallBasePython(): P? {
  val interpreter = requireNotNull(state.baseInterpreter.get()) { "wrong state: base interpreter is not selected" }

  // todo use target config
  val path = if (interpreter is InstallableSelectableInterpreter<P>) {
    installBaseSdk(interpreter.sdk, existingSdks)?.let { fileSystem.wrapSdk(it) }?.homePath
  }
  else interpreter.homePath

  return path
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
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
internal abstract class CustomNewEnvironmentCreator(
  private val name: String,
  model: PythonMutableTargetAddInterpreterModel,
  protected val errorSink: ErrorSink,
) : PythonNewEnvironmentCreator(model) {
  internal lateinit var basePythonComboBox: PythonInterpreterComboBox

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      basePythonComboBox = pythonInterpreterComboBox(
        title = message("sdk.create.custom.base.python"),
        selectedSdkProperty = model.state.baseInterpreter,
        model = model,
        validationRequestor = validationRequestor,
        onPathSelected = model::addInterpreter,
      )

      executableSelector(
        executable = executable,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", name),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", name),
        installAction = createInstallFix(errorSink)
      )

      row("") {
        venvExistenceValidationAlert(validationRequestor) {
          onVenvSelectExisting()
        }
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    basePythonComboBox.initialize(scope, model.baseInterpreters)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    savePathToExecutableToProperties(null)

    // todo think about better error handling
    val selectedBasePython = model.state.baseInterpreter.get()!!
    val basePythonBinaryPath = model.installPythonIfNeeded(selectedBasePython)

    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      is ModuleOrProject.ProjectOnly -> null
    }
    val moduleBasePath = module?.basePath?.let { Path.of(it) }
                         ?: model.projectPathFlows.projectPath.first()
                         ?: error("module base path can't be recognized, both module and project are nulls")

    val newSdk = setupEnvSdk(
      moduleBasePath = moduleBasePath,
      baseSdks = PythonSdkUtil.getAllSdks(),
      basePythonBinaryPath = basePythonBinaryPath,
      installPackages = false
    ).getOr { return it }

    newSdk.persist()
    if (module != null) {
      if (!model.state.makeAvailableForAllProjects.get()) {
        newSdk.setAssociationToModule(module)
      }
      module.baseDir?.refresh(true, false)
    }

    model.addInterpreter(newSdk)

    return Result.success(newSdk)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo =
    InterpreterStatisticsInfo(
      type = interpreterType,
      target = target.toStatisticsField(),
      globalSitePackage = false,
      makeAvailableToAllProjects = model.state.makeAvailableForAllProjects.get(),
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
        detectExecutable()
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
          val installed = model.addInstalledInterpreter(it.homePath!!.toNioPathOrNull()!!, baseInterpreter.languageLevel)
          model.state.baseInterpreter.set(installed)
          installed
        }
      is DetectedSelectableInterpreter, is ExistingSelectableInterpreter, is ManuallyAddedSelectableInterpreter, null -> null
    }

    // installedSdk is null when the selected sdk isn't downloadable
    // model.state.baseInterpreter could be null if no SDK was selected
    val pythonExecutable = Path.of(installedSdk?.homePath ?: model.state.baseInterpreter.get()?.homePath ?: getPythonExecutableString())

    runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.install.fix.title", name, "via pip")) {
      val versionArgs: List<String> = installationVersion?.let { listOf("-v", it) } ?: emptyList()
      when (val r = installExecutableViaPythonScript(pythonExecutable, "-n", name, *versionArgs.toTypedArray())) {
        is Result.Success -> {
          savePathToExecutableToProperties(r.result)
        }
        is Result.Failure -> {
          errorSink.emit(r.error)
        }
      }
    }
  }

  internal abstract val interpreterType: InterpreterType

  internal abstract val executable: ObservableMutableProperty<String>

  internal open val installationVersion: String? = null


  /**
   * Saves the provided path to an executable in the properties of the environment
   *
   * @param [path] The path to the executable that needs to be saved. This may be null when tries to find automatically.
   */
  internal abstract fun savePathToExecutableToProperties(path: Path?)

  protected abstract suspend fun setupEnvSdk(moduleBasePath: Path, baseSdks: List<Sdk>, basePythonBinaryPath: PythonBinary?, installPackages: Boolean): PyResult<Sdk>

  internal abstract suspend fun detectExecutable()

  internal open fun onVenvSelectExisting() {}
}
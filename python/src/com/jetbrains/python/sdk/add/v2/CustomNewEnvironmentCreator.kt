// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.execution.PyExecutionFailure
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.emit
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
internal abstract class CustomNewEnvironmentCreator(private val name: String, model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {
  internal lateinit var basePythonComboBox: PythonInterpreterComboBox

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        basePythonComboBox = pythonInterpreterComboBox(model.state.baseInterpreter,
                                                       model,
                                                       model::addInterpreter,
                                                       model.interpreterLoading)
          .align(Align.FILL)
          .component
      }

      executableSelector(executable,
                         validationRequestor,
                         message("sdk.create.custom.venv.executable.path", name),
                         message("sdk.create.custom.venv.missing.text", name),
                         createInstallFix(errorSink)).component
    }
  }

  override fun onShown() {
    basePythonComboBox.setItems(model.baseInterpreters)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): com.jetbrains.python.Result<Sdk, PyError> {
    savePathToExecutableToProperties(null)

    // todo think about better error handling
    val selectedBasePython = model.state.baseInterpreter.get()!!
    val homePath = model.installPythonIfNeeded(selectedBasePython)

    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      is ModuleOrProject.ProjectOnly -> null
    }
    val newSdk = setupEnvSdk(moduleOrProject.project,
                             module,
                             ProjectJdkTable.getInstance().allJdks.asList(),
                             model.myProjectPathFlows.projectPathWithDefault.first().toString(),
                             homePath,
                             false)
      .getOrElse { return com.jetbrains.python.Result.failure(if (it is PyExecutionFailure) PyError.ExecException(it) else PyError.Message(it.localizedMessage)) }
    newSdk.persist()

    module?.excludeInnerVirtualEnv(newSdk)
    model.addInterpreter(newSdk)

    return com.jetbrains.python.Result.success(newSdk)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo =
    InterpreterStatisticsInfo(interpreterType,
                              target.toStatisticsField(),
                              false,
                              false,
                              false, //presenter.projectLocationContext is WslContext,
                              false, // todo fix for wsl
                              InterpreterCreationMode.CUSTOM)

  /**
   * Creates an installation fix for executable (poetry, pipenv).
   *
   * 1. Checks does a `pythonExecutable` have pip.
   * 2. If no, checks is pip is installed globally.
   * 3. If no, downloads and installs pip from "https://bootstrap.pypa.io/get-pip.py"
   * 4. Runs (pythonExecutable -m) pip install `package_name` --user
   * 5. Reruns `detectExecutable`
   */
  @RequiresEdt
  private fun createInstallFix(errorSink: ErrorSink): ActionLink {
    return ActionLink(message("sdk.create.custom.venv.install.fix.title", name, "via pip")) {
      PythonSdkFlavor.clearExecutablesCache()
      installExecutable(errorSink)
      runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.progress.title.detect.executable")) {
        detectExecutable()
      }
    }
  }

  /**
   * Installs the necessary executable in the Python environment.
   *
   * Initiates a blocking modal progress task to:
   * 1. Ensure pip is installed.
   * 2. Install the executable (specified by `name`) using either a custom installation script or via pip.
   */
  @RequiresEdt
  private fun installExecutable(errorSink: ErrorSink) {
    val pythonExecutable = model.state.baseInterpreter.get()?.homePath ?: getPythonExecutableString()
    runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.install.fix.title", name, "via pip")) {
      if (installationScript != null) {
        val versionArgs: List<String> = installationVersion?.let { listOf("-v", it)  } ?: emptyList()
        val executablePath = installExecutableViaPythonScript(installationScript, pythonExecutable, "-n", name, *versionArgs.toTypedArray())
        executablePath.onSuccess {
        savePathToExecutableToProperties(it)
      }.onFailure {
        errorSink.emit(it.localizedMessage)
        }
      }
    }
  }

  internal abstract val interpreterType: InterpreterType

  internal abstract val executable: ObservableMutableProperty<String>

  internal abstract val installationVersion: String?

  /**
   * The `installationScript` specifies a custom script for installing an executable in the Python environment.
   *
   * If this property is not null, the provided script will be used for installation instead of the default pip installation.
   */
  private val installationScript: Path? = PythonHelpersLocator.findPathInHelpers("pycharm_package_installer.py")


  /**
   * Saves the provided path to an executable in the properties of the environment
   *
   * @param [path] The path to the executable that needs to be saved. This may be null when tries to find automatically.
   */
  internal abstract fun savePathToExecutableToProperties(path: Path?)

  protected abstract suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk>

  internal abstract suspend fun detectExecutable()
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.UV
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.util.ShowingMessageErrorSync
import com.jetbrains.python.venvReader.VirtualEnvReader
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Creates a UV environment creator for the given model.
 *
 * @param module The module context for environment creation. Can be null when creating an interpreter
 *               at the project level (not associated with a specific module). When null, the creator
 *               will navigate to the generic Python existing environment selector instead of the
 *               UV-specific selector if a .venv directory already exists.
 */
internal fun PythonMutableTargetAddInterpreterModel<PathHolder.Eel>.uvCreator(module: Module?): EnvironmentCreatorUv<PathHolder.Eel> =
  EnvironmentCreatorUv(this, module, ShowingMessageErrorSync)

internal class EnvironmentCreatorUv<P : PathHolder>(
  model: PythonMutableTargetAddInterpreterModel<P>,
  private val module: Module?,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator<P>("uv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val toolValidator: ToolValidator<P> = model.uvViewModel.toolValidator
  private val executableFlow = MutableStateFlow(model.uvViewModel.uvExecutable.get())
  private val pythonVersion: ObservableMutableProperty<Version?> = propertyGraph.property(null)
  private lateinit var versionComboBox: ComboBox<Version?>
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.uvViewModel.uvExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> setUvExecutable(path) }
  }

  private val loading = AtomicBooleanProperty(false)

  init {
    model.uvViewModel.uvExecutable.afterChange {
      executableFlow.value = it
    }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        versionComboBox = comboBox(listOf<Version?>(null), textListCellRenderer {
          it?.let { "${it.major}.${it.minor}" } ?: message("python.sdk.uv.default.version")
        })
          .bindItem(pythonVersion)
          .enabledIf(loading.not())
          .component

        cell(AsyncProcessIcon("loader"))
          .align(AlignX.LEFT)
          .customize(UnscaledGaps(0))
          .visibleIf(loading)
      }

      executablePath = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = toolValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", "uv"),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", "uv"),
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
    executablePath.initialize(scope)
    model
      .projectPathFlows
      .projectPathWithDefault
      .combine(executableFlow) { projectPath, executable -> projectPath to executable }
      .onEach { (projectPath, executable) ->
        val venvPath = projectPath.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

        withContext(Dispatchers.IO) {
          venvExistenceValidationState.set(
            if (venvPath.exists())
              VenvExistenceValidationState.Error(Paths.get(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
            else
              VenvExistenceValidationState.Invisible
          )
        }

        versionComboBox.removeAllItems()
        versionComboBox.addItem(null)
        versionComboBox.selectedItem = null

        if (executable?.validationResult?.successOrNull == null) {
          return@onEach
        }

        try {
          loading.set(true)

          val pyProjectTomlPath = projectPath.resolve(PY_PROJECT_TOML)

          val pythonVersions = withContext(Dispatchers.IO) {
            val versionRequest = if (pyProjectTomlPath.exists()) {
              PyProjectToml.parse(pyProjectTomlPath.readText()).project?.requiresPython
            }
            else {
              null
            }

            val cli = createUvCli((executable.pathHolder as PathHolder.Eel).path).getOr { return@withContext emptyList() }
            val uvLowLevel = createUvLowLevel(Path.of(""), cli)
            uvLowLevel.listSupportedPythonVersions(versionRequest)
              .getOr { return@withContext emptyList() }
          }

          pythonVersions.forEach {
            versionComboBox.addItem(it)
          }
        }
        finally {
          loading.set(false)
        }
      }
      .launchIn(scope)

  }

  override fun onVenvSelectExisting() {
    PythonNewProjectWizardCollector.logExistingVenvFixUsed()

    if (module != null) {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = UV)
    }
    else {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PYTHON)
    }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path): PyResult<Sdk> {
    return setupNewUvSdkAndEnv(
      workingDir = moduleBasePath,
      version = pythonVersion.get(),
    )
  }
}
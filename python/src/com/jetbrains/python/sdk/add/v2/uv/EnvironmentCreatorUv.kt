// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
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
import com.intellij.util.text.nullize
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.UV
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState
import com.jetbrains.python.sdk.add.v2.executableSelector
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.statistics.InterpreterType
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
import kotlin.io.path.inputStream


internal class EnvironmentCreatorUv(
  model: PythonMutableTargetAddInterpreterModel,
  private val module: Module?,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator("uv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  private val executableFlow = MutableStateFlow(model.state.uvExecutable.get())
  private lateinit var pythonVersion: ObservableMutableProperty<Version?>
  private lateinit var versionComboBox: ComboBox<Version?>
  private val loading = AtomicBooleanProperty(false)

  init {
    executable.afterChange {
      executableFlow.value = it
    }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        pythonVersion = propertyGraph.property(null)
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

      executableSelector(
        executable = executable,
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
    model
      .projectPathFlows
      .projectPathWithDefault
      .combine(executableFlow) { projectPath, executable -> projectPath to executable  }
      .onEach { (projectPath, executable) ->
        val venvPath = projectPath.resolve(".venv")

        withContext(Dispatchers.IO) {
          venvExistenceValidationState.set(
            if (venvPath.exists())
              VenvExistenceValidationState.Error(Paths.get(".venv"))
            else
              VenvExistenceValidationState.Invisible
          )
        }

        if (executable == "") {
          return@onEach
        }

        versionComboBox.removeAllItems()
        versionComboBox.addItem(null)
        versionComboBox.selectedItem = null

        try {
          loading.set(true)

          val pyProjectTomlPath = projectPath.resolve(PY_PROJECT_TOML)

          val pythonVersions = withContext(Dispatchers.IO) {
            val versionRequest = if (pyProjectTomlPath.exists()) {
              PyProjectToml.parse(pyProjectTomlPath.inputStream()).getOrNull()?.project?.requiresPython
            } else {
              null
            }

            val cli = createUvCli(Path.of(executable))
            val uvLowLevel = createUvLowLevel(Path.of(""), cli)
            uvLowLevel.listSupportedPythonVersions(versionRequest)
              .getOr { return@withContext emptyList() }
          }

          pythonVersions.forEach {
            versionComboBox.addItem(it)
          }
        } finally {
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

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    setUvExecutable(savingPath)
  }

  override suspend fun setupEnvSdk(
    moduleBasePath: Path,
    baseSdks: List<Sdk>,
    basePythonBinaryPath: PythonBinary?,
    installPackages: Boolean
  ): PyResult<Sdk> {
    return setupNewUvSdkAndEnv(moduleBasePath, baseSdks, pythonVersion.get())
  }

  override suspend fun detectExecutable() {
    model.detectUvExecutable()
  }

}
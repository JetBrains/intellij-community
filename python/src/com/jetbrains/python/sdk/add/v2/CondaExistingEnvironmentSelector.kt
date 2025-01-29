// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.layout.predicate
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.asPythonResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class CondaExistingEnvironmentSelector(model: PythonAddInterpreterModel, private val errorSink: ErrorSink) : PythonExistingEnvironmentConfigurator(model) {
  private lateinit var envComboBox: ComboBox<PyCondaEnv?>

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    with(panel) {
      executableSelector(state.condaExecutable,
                         validationRequestor,
                         message("sdk.create.custom.venv.executable.path", "conda"),
                         message("sdk.create.custom.venv.missing.text", "conda"),
                         createInstallCondaFix(model, errorSink))
        .displayLoaderWhen(model.condaEnvironmentsLoading, scope = model.scope, uiContext = model.uiContext)

      row(message("sdk.create.custom.env.creation.type")) {
        val condaEnvironmentsLoaded = model.condaEnvironmentsLoading.predicate(model.scope) { !it }

        envComboBox = comboBox(emptyList(), CondaEnvComboBoxListCellRenderer())
          .bindItem(state.selectedCondaEnv)
          .displayLoaderWhen(model.condaEnvironmentsLoading, makeTemporaryEditable = true,
                             scope = model.scope, uiContext = model.uiContext)

          .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(state.condaExecutable))
          .validationOnInput {
            return@validationOnInput if (it.isVisible && it.selectedItem == null) ValidationInfo(message("python.sdk.conda.no.env.selected.error")) else null
          }
          .component

        link(message("sdk.create.custom.conda.refresh.envs"), action = { onReloadCondaEnvironments() })
          .visibleIf(condaEnvironmentsLoaded)
      }.visibleIf(state.condaExecutable.notEqualsTo(UNKNOWN_EXECUTABLE))
    }
  }

  private fun onReloadCondaEnvironments() {
    model.scope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
      model.condaEnvironmentsLoading.value = true
      model.detectCondaEnvironmentsOrError(errorSink)
      model.condaEnvironmentsLoading.value = false
    }
  }

  override fun onShown() {
    model.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      model.condaEnvironments.collectLatest { environments ->
        envComboBox.removeAllItems()
        environments.forEach(envComboBox::addItem)
      }
    }


    //model.scope.launch(start = CoroutineStart.UNDISPATCHED) {
    //  presenter.currentCondaExecutableFlow
    //    .debounce(1.seconds)
    //    .collectLatest { condaExecutablePath ->
    //      withContext(Dispatchers.EDT + modalityState) {
    //        val pathOnTarget = condaExecutablePath?.let { presenter.getPathOnTarget(it) }
    //        if (pathOnTarget != null) {
    //          reloadCondaEnvironments(pathOnTarget)
    //        }
    //        else {
    //          loadingCondaEnvironments.value = false
    //        }
    //      }
    //    }
    //}

    //state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
    //  presenter.currentCondaExecutableFlow.collectLatest {
    //    loadingCondaEnvironments.value = true
    //  }
    //}
    //
    //state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
    //  presenter.detectingCondaExecutable.collectLatest { isDetecting ->
    //    if (isDetecting) loadingCondaEnvironments.value = true
    //  }
    //}
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): Result<Sdk, PyError> =
    model.selectCondaEnvironment(base = false).asPythonResult()

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField()
    val identity = model.state.selectedCondaEnv.get()?.envIdentity as? PyCondaEnvIdentity.UnnamedEnv
    val selectedConda = if (identity?.isBase == true) InterpreterType.BASE_CONDA else InterpreterType.CONDAVENV
    return InterpreterStatisticsInfo(selectedConda,
                                     statisticsTarget,
                                     false,
                                     false,
                                     true,
      //presenter.projectLocationContext is WslContext,
                                     false,
                                     InterpreterCreationMode.CUSTOM)
  }

}
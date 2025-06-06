// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.awt.event.ActionEvent
import javax.swing.AbstractAction


internal class CondaExistingEnvironmentSelector(model: PythonAddInterpreterModel, private val errorSink: ErrorSink) : PythonExistingEnvironmentConfigurator(model) {
  private lateinit var envComboBox: ComboBox<PyCondaEnv?>
  private lateinit var condaExecutable: TextFieldWithBrowseButton
  private lateinit var reloadLink: ActionLink
  private val isReloadLinkVisible = AtomicBooleanProperty(false)


  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      condaExecutable = executableSelector(
        executable = state.condaExecutable,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", "conda"),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", "conda"),
        installAction = createInstallCondaFix(model, errorSink)
      ).component

      row(message("sdk.create.custom.env.creation.type")) {
        envComboBox = comboBox(
          items = emptyList(),
          renderer = CondaEnvComboBoxListCellRenderer()
        ).withExtendableTextFieldEditor()
          .bindItem(state.selectedCondaEnv)
          .validationRequestor(
            validationRequestor
              and WHEN_PROPERTY_CHANGED(state.selectedCondaEnv)
              and WHEN_PROPERTY_CHANGED(state.condaExecutable)
          )
          .validationOnInput {
            return@validationOnInput if (it.isVisible && it.selectedItem == null) ValidationInfo(message("python.sdk.conda.no.env.selected.error")) else null
          }
          .component

        reloadLink = link(
          text = message("sdk.create.custom.conda.refresh.envs"),
          action = { }
        ).visibleIf(isReloadLinkVisible).component

      }.visibleIf(state.condaExecutable.notEqualsTo(UNKNOWN_EXECUTABLE))
    }
  }

  private fun onReloadCondaEnvironments(scope: CoroutineScope) {
    scope.launch(Dispatchers.EDT) {
      model.condaEnvironmentsLoading.value = true
      model.detectCondaEnvironmentsOrError(errorSink)
      model.condaEnvironmentsLoading.value = false
    }
  }

  override fun onShown(scope: CoroutineScope) {
    scope.launch(Dispatchers.EDT) {
      model.condaEnvironments.collectLatest { environments ->
        envComboBox.removeAllItems()
        environments.forEach(envComboBox::addItem)
      }
    }

    reloadLink.action = object : AbstractAction(message("sdk.create.custom.conda.refresh.envs")) {
      override fun actionPerformed(e: ActionEvent?) {
        onReloadCondaEnvironments(scope)
      }
    }

    model.condaEnvironmentsLoading.onEach { isLoading ->
      isReloadLinkVisible.set(!isLoading)
    }.launchIn(scope + Dispatchers.EDT)

    envComboBox.displayLoaderWhen(
      loading = model.condaEnvironmentsLoading,
      makeTemporaryEditable = true,
      scope = scope,
    )

    condaExecutable.displayLoaderWhen(
      loading = model.condaEnvironmentsLoading,
      scope = scope,
    )
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    return model.selectCondaEnvironment(base = false)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val identity = model.state.selectedCondaEnv.get()?.envIdentity as? PyCondaEnvIdentity.UnnamedEnv
    val selectedConda = if (identity?.isBase == true) InterpreterType.BASE_CONDA else InterpreterType.CONDAVENV
    return InterpreterStatisticsInfo(
      type = selectedConda,
      target = target.toStatisticsField(),
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = true,
      isWSLContext = false,
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}

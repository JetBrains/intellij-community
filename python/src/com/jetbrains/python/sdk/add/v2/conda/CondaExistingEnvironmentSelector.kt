// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
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


internal class CondaExistingEnvironmentSelector<P : PathHolder>(model: PythonAddInterpreterModel<P>) : PythonExistingEnvironmentConfigurator<P>(model) {
  private lateinit var envComboBox: ComboBox<PyCondaEnv?>
  private lateinit var condaExecutable: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>
  private lateinit var reloadLink: ActionLink
  private val isReloadLinkVisible = AtomicBooleanProperty(false)


  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      condaExecutable = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = model.condaViewModel.toolValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", "conda"),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", "conda"),
        installAction = createInstallCondaFix(model)
      )

      rowsRange {
        row(message("sdk.create.custom.env.creation.type")) {
          envComboBox = comboBox(
            items = emptyList(),
            renderer = CondaEnvComboBoxListCellRenderer()
          ).withExtendableTextFieldEditor()
            .bindItem(model.condaViewModel.selectedCondaEnv)
            .validationRequestor(
              validationRequestor
                and WHEN_PROPERTY_CHANGED(model.modificationCounter)
                and WHEN_PROPERTY_CHANGED(model.condaViewModel.selectedCondaEnv)
                and WHEN_PROPERTY_CHANGED(model.condaViewModel.condaExecutable)
                and WHEN_PROPERTY_CHANGED(isReloadLinkVisible)
            )
            .validationOnInput {
              if (!it.isVisible) return@validationOnInput null

              val environmentsResult = model.condaViewModel.condaEnvironmentsResult.value
              when {
                environmentsResult == null || !isReloadLinkVisible.get() -> {
                  ValidationInfo(message("python.add.sdk.panel.wait")).asWarning()
                }
                environmentsResult is Result.Failure -> {
                  ValidationInfo(environmentsResult.error.message)
                }
                it.selectedItem == null -> {
                  ValidationInfo(message("python.sdk.conda.no.env.selected.error"))
                }
                else -> null
              }
            }
            .align(Align.FILL)
            .applyToComponent {
              preferredSize = JBUI.size(preferredSize)
            }
            .component
        }

        row {
          reloadLink = link(
            text = message("sdk.create.custom.conda.refresh.envs"),
            action = { }
          )
            .align(AlignX.RIGHT)
            .visibleIf(isReloadLinkVisible).component
        }
      }.visibleIf(model.condaViewModel.condaExecutable.transform { it?.validationResult?.successOrNull != null })
    }
  }

  override fun onShown(scope: CoroutineScope) {
    scope.launch(Dispatchers.EDT) {
      model.condaViewModel.condaEnvironmentsResult.collectLatest { environmentsResult ->
        envComboBox.removeAllItems()
        environmentsResult?.successOrNull?.forEach(envComboBox::addItem)
      }
    }

    reloadLink.action = object : AbstractAction(message("sdk.create.custom.conda.refresh.envs")) {
      override fun actionPerformed(e: ActionEvent?) {
        model.condaViewModel.detectCondaEnvironments()
      }
    }

    model.condaViewModel.condaEnvironmentsLoading.onEach { isLoading ->
      isReloadLinkVisible.set(!isLoading)
    }.launchIn(scope + Dispatchers.EDT)

    envComboBox.displayLoaderWhen(
      loading = model.condaViewModel.condaEnvironmentsLoading,
      makeTemporaryEditable = true,
      scope = scope,
    )
    condaExecutable.initialize(scope)
    condaExecutable.displayLoaderWhen(
      loading = model.condaViewModel.condaEnvironmentsLoading,
      scope = scope,
    )
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    return model.selectCondaEnvironment(moduleOrProject, base = false)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val identity = model.condaViewModel.selectedCondaEnv.get()?.envIdentity as? PyCondaEnvIdentity.UnnamedEnv
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

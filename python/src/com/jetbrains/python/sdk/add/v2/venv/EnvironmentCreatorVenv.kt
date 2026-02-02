// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonInterpreterComboBox
import com.jetbrains.python.sdk.add.v2.PythonInterpreterCreationTargets
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.ValidatedPathField
import com.jetbrains.python.sdk.add.v2.VenvAlreadyExistsError
import com.jetbrains.python.sdk.add.v2.pythonInterpreterComboBox
import com.jetbrains.python.sdk.add.v2.toStatisticsField
import com.jetbrains.python.sdk.add.v2.validatablePathField
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

class EnvironmentCreatorVenv<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>) : PythonNewEnvironmentCreator<P>(model) {
  private lateinit var versionComboBox: PythonInterpreterComboBox<P>
  private lateinit var venvPathField: ValidatedPathField<Unit, P, ValidatedPath.Folder<P>>
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?>? = null
  override val toolExecutablePersister: suspend (P) -> Unit = { }

  private val venvAlreadyExistsError = propertyGraph.property<VenvAlreadyExistsError<P>?>(null)
  private val venvAlreadyExistsErrorMessage: ObservableMutableProperty<String> = propertyGraph.property("")

  private var locationModified = false

  init {
    propertyGraph.dependsOn(venvAlreadyExistsError, model.venvViewModel.backProperty, deleteWhenChildModified = false) {
      model.venvViewModel.backProperty.get()?.validationResult?.errorOrNull as? VenvAlreadyExistsError<P>
    }
    propertyGraph.dependsOn(venvAlreadyExistsErrorMessage, venvAlreadyExistsError, deleteWhenChildModified = false) {
      venvAlreadyExistsError.get()?.message ?: ""
    }
  }


  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    val secondFixLink = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
      PythonNewProjectWizardCollector.logExistingVenvFixUsed()
      val venvAlreadyExistsError = venvAlreadyExistsError.get()

      venvAlreadyExistsError?.let { error ->
        val interpreter = error.detectedSelectableInterpreter
        model.state.selectedInterpreter.set(interpreter)
        model.navigator.navigateTo(
          newMethod = PythonInterpreterSelectionMethod.SELECT_EXISTING,
          newManager = PythonSupportedEnvironmentManagers.PYTHON
        )
      }
    }

    with(panel) {
      versionComboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.custom.base.python"),
        selectedSdkProperty = model.state.baseInterpreter,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedSystemPython,
      )


      venvPathField = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = model.venvViewModel.venvValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.location"),
        missingExecutableText = null,
        isFileSelectionMode = false,
      )


      row("") {
        validationTooltip(venvAlreadyExistsErrorMessage, secondFixLink)
          .align(Align.FILL)
      }.visibleIf(venvAlreadyExistsError.isNotNull())

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(model.venvViewModel.inheritSitePackages)
      }
      row("") {
        checkBox(message("available.to.all.projects"))
          .bindSelected(model.venvViewModel.makeAvailableForAllProjects)
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    versionComboBox.initialize(scope, model.baseInterpreters)
    venvPathField.initialize(scope)


    model.projectPathFlows.projectPathWithDefault.onEach {
      if (locationModified) return@onEach

      model.venvViewModel.venvValidator.autodetectFolder()
    }.launchIn(scope + Dispatchers.EDT)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val venv = model.venvViewModel.backProperty.get()?.pathHolder
               ?: return PyResult.localizedError(message("no.venv.path.specified"))
    return model.setupVirtualenv(venv, moduleOrProject)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(
      type = InterpreterType.VIRTUALENV,
      target = target.toStatisticsField(),
      globalSitePackage = model.venvViewModel.inheritSitePackages.get(),
      makeAvailableToAllProjects = model.venvViewModel.makeAvailableForAllProjects.get(),
      previouslyConfigured = false,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}

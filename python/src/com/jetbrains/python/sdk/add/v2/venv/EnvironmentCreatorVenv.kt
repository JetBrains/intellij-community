// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
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
import com.jetbrains.python.sdk.add.v2.validatableVenvField
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

  private val locationValidationMessage = propertyGraph.property("Current location already exists")
  private var locationModified = false


  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    val secondFixLink = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
      PythonNewProjectWizardCollector.logExistingVenvFixUsed()
      val venvAlreadyExistsError = model.state.venvPath.get()?.validationResult?.errorOrNull as? VenvAlreadyExistsError<P>

      venvAlreadyExistsError?.let { error ->
        val interpreter = error.detectedSelectableInterpreter.also {
          model.addManuallyAddedInterpreter(it)
        }

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
        onPathSelected = model::addManuallyAddedInterpreter,
      )


      venvPathField = validatableVenvField(
        propertyGraph = propertyGraph,
        fileSystem = model.fileSystem,
        backProperty = model.state.venvPath,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.location"),
        missingExecutableText = null,
        selectedPathValidator = { model.fileSystem.validateVenv(it) }
      )


      row("") {
        validationTooltip(locationValidationMessage,  secondFixLink)
          .align(Align.FILL)
      }.visibleIf(venvPathField.backProperty.transform { it?.validationResult?.errorOrNull }.isNotNull())

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(model.state.inheritSitePackages)
      }
      row("") {
        checkBox(message("available.to.all.projects"))
          .bindSelected(model.state.makeAvailableForAllProjects)
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    versionComboBox.initialize(scope, model.baseInterpreters)
    venvPathField.initialize(scope)


    model.projectPathFlows.projectPathWithDefault.onEach {
      if (locationModified) return@onEach

      val suggestedVirtualEnv = model.fileSystem.suggestVenv(it)
      model.state.venvPath.set(suggestedVirtualEnv)
    }.launchIn(scope + Dispatchers.EDT)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val venv = model.state.venvPath.get()?.pathHolder
               ?: return PyResult.localizedError(message("no.venv.path.specified"))
    return model.setupVirtualenv(venv, moduleOrProject)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(
      type = InterpreterType.VIRTUALENV,
      target = target.toStatisticsField(),
      globalSitePackage = model.state.inheritSitePackages.get(),
      makeAvailableToAllProjects = model.state.makeAvailableForAllProjects.get(),
      previouslyConfigured = false,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}

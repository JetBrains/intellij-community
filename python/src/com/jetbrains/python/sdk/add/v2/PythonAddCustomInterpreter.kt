// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindItem
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.*
import com.jetbrains.python.util.ErrorSink
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

class PythonAddCustomInterpreter(val model: PythonMutableTargetAddInterpreterModel, val moduleOrProject: ModuleOrProject? = null, projectPathFlow: Flow<Path>? = null, errorSink: ErrorSink) {

  private val propertyGraph = model.propertyGraph
  private val selectionMethod = propertyGraph.property(PythonInterpreterSelectionMethod.CREATE_NEW)
  private val _createNew = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.CREATE_NEW)
  private val _selectExisting = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.SELECT_EXISTING)
  private val newInterpreterManager = propertyGraph.property(VIRTUALENV)
  private val existingInterpreterManager = propertyGraph.property(PYTHON)

  private val newInterpreterCreators = mapOf(
    VIRTUALENV to EnvironmentCreatorVenv(model),
    CONDA to CondaNewEnvironmentCreator(model, errorSink),
    PIPENV to EnvironmentCreatorPip(model),
    POETRY to EnvironmentCreatorPoetry(model, moduleOrProject),
    UV to EnvironmentCreatorUv(model, moduleOrProject),
  )

  private val existingInterpreterSelectors = buildMap {
    put(PYTHON, PythonExistingEnvironmentSelector(model))
    put(CONDA, CondaExistingEnvironmentSelector(model, errorSink))
    if (moduleOrProject != null) put(POETRY, PoetryExistingEnvironmentSelector(model, moduleOrProject))
  }

  val currentSdkManager: PythonAddEnvironment
    get() {
      return if (_selectExisting.get()) existingInterpreterSelectors[existingInterpreterManager.get()]!!
      else newInterpreterCreators[newInterpreterManager.get()]!!
    }


  fun buildPanel(outerPanel: Panel, validationRequestor: DialogValidationRequestor) {
    with(model) {
      navigator.selectionMethod = selectionMethod
      navigator.newEnvManager = newInterpreterManager
      navigator.existingEnvManager = existingInterpreterManager
    }

    with(outerPanel) {
      buttonsGroup {
        row(message("sdk.create.custom.env.creation.type")) {
          val newRadio = radioButton(message("sdk.create.custom.generate.new"), PythonInterpreterSelectionMethod.CREATE_NEW).onChanged {
            selectionMethod.set(
              if (it.isSelected) PythonInterpreterSelectionMethod.CREATE_NEW else PythonInterpreterSelectionMethod.SELECT_EXISTING)
          }.component

          val existingRadio = radioButton(message("sdk.create.custom.select.existing"), PythonInterpreterSelectionMethod.SELECT_EXISTING).component

          selectionMethod.afterChange {
            newRadio.isSelected = it == PythonInterpreterSelectionMethod.CREATE_NEW
            existingRadio.isSelected = it == PythonInterpreterSelectionMethod.SELECT_EXISTING
          }
        }
      }.bind({ selectionMethod.get() }, { selectionMethod.set(it) })

      row(message("sdk.create.custom.type")) {
        comboBox(newInterpreterCreators.keys, PythonEnvironmentComboBoxRenderer())
          .bindItem(newInterpreterManager)
          .widthGroup("env_aligned")
          .visibleIf(_createNew)

        comboBox(existingInterpreterSelectors.keys, PythonEnvironmentComboBoxRenderer())
          .bindItem(existingInterpreterManager)
          .widthGroup("env_aligned")
          .visibleIf(_selectExisting)
      }

      newInterpreterCreators.forEach { (type, creator) ->
        rowsRange {
          creator.buildOptions(this,
                               validationRequestor
                                 and WHEN_PROPERTY_CHANGED(selectionMethod)
                                 and WHEN_PROPERTY_CHANGED(newInterpreterManager))
        }.visibleIf(_createNew and newInterpreterManager.equalsTo(type))
      }

      existingInterpreterSelectors.forEach { (type, selector) ->
        rowsRange {
          selector.buildOptions(this,
                                validationRequestor
                                  and WHEN_PROPERTY_CHANGED(selectionMethod)
                                  and WHEN_PROPERTY_CHANGED(existingInterpreterManager))
        }.visibleIf(_selectExisting and existingInterpreterManager.equalsTo(type))
      }

    }
  }


  fun onShown() {
    newInterpreterCreators.values.forEach(PythonAddEnvironment::onShown)
    existingInterpreterSelectors.values.forEach(PythonAddEnvironment::onShown)
  }

  fun createStatisticsInfo(): InterpreterStatisticsInfo {
    return currentSdkManager.createStatisticsInfo(PythonInterpreterCreationTargets.LOCAL_MACHINE)
  }

}
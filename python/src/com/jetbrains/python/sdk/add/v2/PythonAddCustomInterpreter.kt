// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindItem
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.*
import com.jetbrains.python.sdk.add.v2.conda.CondaExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.conda.CondaNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.hatch.HatchExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.hatch.HatchNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.poetry.EnvironmentCreatorPoetry
import com.jetbrains.python.sdk.add.v2.poetry.PoetryExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.uv.EnvironmentCreatorUv
import com.jetbrains.python.sdk.add.v2.uv.UvExistingEnvironmentSelector
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

class PythonAddCustomInterpreter(
  val model: PythonMutableTargetAddInterpreterModel,
  val module: Module?,
  private val errorSink: ErrorSink,
  private val limitExistingEnvironments: Boolean,
) {

  private val propertyGraph = model.propertyGraph
  private val selectionMethod = propertyGraph.property(PythonInterpreterSelectionMethod.CREATE_NEW)
  private val _createNew = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.CREATE_NEW)
  private val _selectExisting = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.SELECT_EXISTING)

  @Internal
  val newInterpreterManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers> = propertyGraph.property(VIRTUALENV)

  private val existingInterpreterManager = propertyGraph.property(PYTHON)

  private val newInterpreterCreators = mapOf(
    VIRTUALENV to EnvironmentCreatorVenv(model),
    CONDA to CondaNewEnvironmentCreator(model, errorSink),
    PIPENV to EnvironmentCreatorPip(model, errorSink),
    POETRY to EnvironmentCreatorPoetry(model, module, errorSink),
    UV to EnvironmentCreatorUv(model, module, errorSink),
    HATCH to HatchNewEnvironmentCreator(model, errorSink),
  )

  private val existingInterpreterSelectors = buildMap {
    put(PYTHON, PythonExistingEnvironmentSelector(model, module))
    put(CONDA, CondaExistingEnvironmentSelector(model, errorSink))
    if (!limitExistingEnvironments) {
      put(POETRY, PoetryExistingEnvironmentSelector(model, module))
      put(UV, UvExistingEnvironmentSelector(model, module))
      put(HATCH, HatchExistingEnvironmentSelector(model))
    }
  }

  val currentSdkManager: PythonAddEnvironment
    get() {
      return if (_selectExisting.get()) existingInterpreterSelectors[existingInterpreterManager.get()]!!
      else newInterpreterCreators[newInterpreterManager.get()]!!
    }


  fun setupUI(outerPanel: Panel, validationRequestor: DialogValidationRequestor) {
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
          creator.setupUI(
            panel = this,
            validationRequestor = validationRequestor
              and WHEN_PROPERTY_CHANGED(model.modificationCounter)
              and WHEN_PROPERTY_CHANGED(selectionMethod)
              and WHEN_PROPERTY_CHANGED(newInterpreterManager),
          )
        }.visibleIf(_createNew and newInterpreterManager.equalsTo(type))
      }

      existingInterpreterSelectors.forEach { (type, selector) ->
        rowsRange {
          selector.setupUI(
            panel = this,
            validationRequestor = validationRequestor
              and WHEN_PROPERTY_CHANGED(model.modificationCounter)
              and WHEN_PROPERTY_CHANGED(selectionMethod)
              and WHEN_PROPERTY_CHANGED(existingInterpreterManager),
          )
        }.visibleIf(_selectExisting and existingInterpreterManager.equalsTo(type))
      }

    }
  }


  fun onShown(scope: CoroutineScope) {
    newInterpreterCreators.values.forEach { it.onShown(scope) }
    existingInterpreterSelectors.values.forEach { it.onShown(scope) }
  }

  fun createStatisticsInfo(): InterpreterStatisticsInfo {
    return currentSdkManager.createStatisticsInfo(PythonInterpreterCreationTargets.LOCAL_MACHINE)
  }
}
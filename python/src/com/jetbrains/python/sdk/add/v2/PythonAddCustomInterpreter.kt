// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindItem
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.findPanelExtension
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.*
import com.jetbrains.python.sdk.add.v2.conda.CondaExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.conda.CondaNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.hatch.HatchExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.hatch.HatchNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.pipenv.EnvironmentCreatorPip
import com.jetbrains.python.sdk.add.v2.poetry.EnvironmentCreatorPoetry
import com.jetbrains.python.sdk.add.v2.poetry.PoetryExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.uv.EnvironmentCreatorUv
import com.jetbrains.python.sdk.add.v2.uv.UvExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.venv.EnvironmentCreatorVenv
import com.jetbrains.python.sdk.add.v2.venv.PythonExistingEnvironmentSelector
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

class ValidationInfoError(val validationInfo: ValidationInfo) : MessageError(validationInfo.message)

internal class PythonAddCustomInterpreter<P : PathHolder>(
  val model: PythonMutableTargetAddInterpreterModel<P>,
  val module: Module?,
  private val errorSink: ErrorSink,
  private val limitExistingEnvironments: Boolean,
) {

  private val propertyGraph = model.propertyGraph
  private val selectionMethod = propertyGraph.property(
    if (!model.fileSystem.isReadOnly)
      PythonInterpreterSelectionMethod.CREATE_NEW
    else
      PythonInterpreterSelectionMethod.SELECT_EXISTING
  )
  private val _createNew = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.CREATE_NEW)
  private val _selectExisting = propertyGraph.booleanProperty(selectionMethod, PythonInterpreterSelectionMethod.SELECT_EXISTING)

  @Internal
  val newInterpreterManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers> = propertyGraph.property(VIRTUALENV)

  private val existingInterpreterManager = propertyGraph.property(PYTHON)

  private val newInterpreterCreators = if (model.fileSystem.isReadOnly) emptyMap()
  else mapOf(
    VIRTUALENV to { EnvironmentCreatorVenv(model) },
    CONDA to { CondaNewEnvironmentCreator(model) },
    PIPENV to { EnvironmentCreatorPip(model, errorSink) },
    POETRY to { EnvironmentCreatorPoetry(model, module, errorSink) },
    UV to { EnvironmentCreatorUv(model, module, errorSink) },
    HATCH to { HatchNewEnvironmentCreator(model, errorSink) },
  ).filterKeys { it.isFSSupported(model.fileSystem) }.mapValues { it.value() }

  private val existingInterpreterSelectors = buildMap {
    put(PYTHON) { PythonExistingEnvironmentSelector(model, module) }
    put(CONDA) { CondaExistingEnvironmentSelector(model) }
    if (!limitExistingEnvironments) {
      put(POETRY) { PoetryExistingEnvironmentSelector(model, module) }
      put(UV) { UvExistingEnvironmentSelector(model, module) }
      put(HATCH) { HatchExistingEnvironmentSelector(model) }
    }
  }.filterKeys { it.isFSSupported(model.fileSystem) }.mapValues { it.value() }

  val currentSdkManager: PythonAddEnvironment<P>
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
      if (!model.fileSystem.isReadOnly) {
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
      }

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

      module?.project?.let { project ->
        (model.fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration?.let { configuration ->
          findPanelExtension(project, configuration)?.let { extension ->
            collapsibleGroup(message("sdk.create.custom.target.specific.properties"), indent = false) {
              extension.extendDialogPanelWithOptionalFields(this)
              model.state.targetPanelExtension.set(extension)
            }
          }
        }
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

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedComboBoxEditor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.util.lateinitVal
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.isFailure
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.nio.file.Path
import javax.swing.JList

internal sealed class HatchUIError(message: String) : MessageError(message) {
  class HatchEnvironmentIsNotSelected : HatchUIError(
    message("sdk.create.custom.hatch.error.environment.is.not.selected")
  )

  class HatchExecutablePathIsNotValid(hatchExecutablePath: String?) : HatchUIError(
    message("sdk.create.custom.hatch.error.hatch.executable.path.is.not.valid",
            hatchExecutablePath)
  )

}

internal fun String.toPath(): PyResult<Path> {
  return when (val selectedPath = Path.of(this)) {
    null -> Result.failure(HatchUIError.HatchExecutablePathIsNotValid(this))
    else -> Result.success(selectedPath)
  }
}

private class HatchEnvComboBoxListCellRenderer(val contentFlow: StateFlow<PyResult<List<*>>?>) : ColoredListCellRenderer<HatchVirtualEnvironment>() {
  override fun customizeCellRenderer(list: JList<out HatchVirtualEnvironment?>, value: HatchVirtualEnvironment?, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (val result = contentFlow.value) {
      null -> {
        icon = AllIcons.Process.Step_1
        append(message("sdk.create.custom.hatch.environment.loading"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      }
      is Result.Failure -> {
        append(message("sdk.create.custom.hatch.error.no.environments.to.select"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      }
      is Result.Success -> {
        if (result.result.isEmpty() || value == null) {
          append(message("sdk.create.custom.hatch.error.no.environments.to.select"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
        else {
          icon = when (value.pythonVirtualEnvironment) {
            null -> AllIcons.Nodes.Folder
            is PythonVirtualEnvironment.Existing -> PythonIcons.Python.PythonClosed
            is PythonVirtualEnvironment.NotExisting -> AllIcons.Nodes.Folder
          }
          append(value.hatchEnvironment.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          value.pythonVirtualEnvironment?.pythonHomePath?.let { pythonHomePath ->
            append("\t", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(pythonHomePath.toString(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }
      }
    }
  }
}

internal class HatchEnvironmentComboBox<P : PathHolder>(
  model: PythonAddInterpreterModel<P>,
) : ComboBox<HatchVirtualEnvironment?>() {
  init {
    renderer = HatchEnvComboBoxListCellRenderer(model.hatchViewModel.availableEnvironments)
    editor = FixedComboBoxEditor()
  }

  @Synchronized
  internal fun syncWithEnvs(
    environmentsResult: PyResult<List<HatchVirtualEnvironment>>,
    isFilterOnlyExisting: Boolean = false,
  ) {
    removeAllItems()
    val environments = environmentsResult.getOr { return }
    environments.filter { !isFilterOnlyExisting || it.pythonVirtualEnvironment is PythonVirtualEnvironment.Existing }.forEach {
      addItem(it)
    }
  }
}


private fun <P : PathHolder> Panel.addEnvironmentComboBox(
  model: PythonAddInterpreterModel<P>,
  validationRequestor: DialogValidationRequestor,
  isGenerateNewMode: Boolean,
): HatchEnvironmentComboBox<P> {
  val environmentAlreadyExists = AtomicBooleanProperty(false)

  val environmentComboBox = HatchEnvironmentComboBox(model)

  val hatchEnvironmentProperty = if (isGenerateNewMode)
    model.hatchViewModel.selectedEnvFromAvailable
  else
    model.hatchViewModel.selectedEnvFromExisting

  row(message("sdk.create.custom.hatch.environment")) {
    cell(environmentComboBox)
      .bindItem(hatchEnvironmentProperty)
      .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(hatchEnvironmentProperty))
      .validationInfo { component ->
        environmentAlreadyExists.set(false)
        when {
          !component.isVisible || !component.isEnabled -> null
          component.item == null -> {
            ValidationInfo("")
          }
          isGenerateNewMode && component.item?.pythonVirtualEnvironment is PythonVirtualEnvironment.Existing -> {
            environmentAlreadyExists.set(true)
            ValidationInfo(message("sdk.create.custom.hatch.environment.exists"))
          }
          else -> null
        }
      }
      .align(Align.FILL)
      .applyToComponent {
        preferredSize = JBUI.size(preferredSize)
      }
  }

  row("") {
    validationTooltip(
      message = message("sdk.create.custom.hatch.environment.exists"),
      firstActionLink = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
        PythonNewProjectWizardCollector.logExistingVenvFixUsed()
        model.hatchViewModel.selectedEnvFromExisting.set(environmentComboBox.item)
        model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PythonSupportedEnvironmentManagers.HATCH)
      },
      validationType = ValidationType.ERROR
    ).align(Align.FILL)
  }.visibleIf(environmentAlreadyExists)
  return environmentComboBox
}

private fun <P : PathHolder> Panel.addExecutableSelector(
  model: PythonMutableTargetAddInterpreterModel<P>,
  validationRequestor: DialogValidationRequestor,
  installHatchActionLink: ActionLink? = null,
): ValidatedPathField<Version, P, ValidatedPath.Executable<P>> {


  val executablePath = validatablePathField(
    fileSystem = model.fileSystem,
    pathValidator = model.hatchViewModel.toolValidator,
    validationRequestor = validationRequestor,
    labelText = message("sdk.create.custom.venv.executable.path", "hatch"),
    missingExecutableText = message("sdk.create.custom.venv.missing.text", "hatch"),
    installAction = installHatchActionLink,
  )

  return executablePath
}

internal data class HatchFormFields<P : PathHolder>(
  val environmentComboBox: HatchEnvironmentComboBox<P>,
  val basePythonComboBox: PythonInterpreterComboBox<P>?,
  val validatedPathField: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>,
) {
  fun onShown(scope: CoroutineScope, model: PythonMutableTargetAddInterpreterModel<P>, isFilterOnlyExisting: Boolean) {
    model.hatchViewModel.availableEnvironments.onEach { environmentsResult ->
      when (environmentsResult) {
        null -> environmentComboBox.isEnabled = false
        else -> {
          environmentComboBox.isEnabled = true
          environmentComboBox.syncWithEnvs(environmentsResult, isFilterOnlyExisting = isFilterOnlyExisting)
          if (environmentsResult.isFailure) {
            model.hatchViewModel.selectedEnvFromAvailable.set(null)
            model.hatchViewModel.selectedEnvFromExisting.set(null)
          }
        }
      }
    }.launchIn(scope + Dispatchers.EDT)

    with(validatedPathField) {
      initialize(scope)
      model.hatchViewModel.hatchExecutable.afterChange { executable ->
        if (executable != model.hatchViewModel.hatchExecutable.get()) {
          model.hatchViewModel.hatchExecutable.set(executable)
        }
      }
    }
  }
}

internal fun <P : PathHolder> Panel.buildHatchFormFields(
  model: PythonMutableTargetAddInterpreterModel<P>,
  validationRequestor: DialogValidationRequestor,
  isGenerateNewMode: Boolean = false,
  installHatchActionLink: ActionLink? = null,
): HatchFormFields<P> {

  val executablePath = addExecutableSelector(
    model,
    validationRequestor,
    installHatchActionLink
  )
  var environmentComboBox: HatchEnvironmentComboBox<P> by lateinitVal()
  var basePythonComboBox: PythonInterpreterComboBox<P>? = null

  rowsRange {
    environmentComboBox = addEnvironmentComboBox(
      model = model,
      validationRequestor = validationRequestor,
      isGenerateNewMode = isGenerateNewMode
    )

    if (isGenerateNewMode) {
      basePythonComboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.custom.base.python"),
        selectedSdkProperty = model.state.baseInterpreter,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedSystemPython,
      )
    }
  }.visibleIf(model.hatchViewModel.hatchExecutable.transform { it?.validationResult?.successOrNull != null })


  return HatchFormFields(environmentComboBox, basePythonComboBox, executablePath)
}


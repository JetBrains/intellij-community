// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.ui.layout.ComponentPredicate
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.isFailure
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import javax.swing.JList

internal sealed class HatchUIError(message: String) : MessageError(message) {
  class ProjectIsNotSelected : HatchUIError(
    message("sdk.create.custom.hatch.error.project.is.not.selected")
  )

  class ModuleIsNotSelected : HatchUIError(
    message("sdk.create.custom.hatch.error.module.is.not.selected")
  )

  class HatchEnvironmentIsNotSelected : HatchUIError(
    message("sdk.create.custom.hatch.error.environment.is.not.selected")
  )

  class HatchExecutablePathIsNotValid(hatchExecutablePath: String?) : HatchUIError(
    message("sdk.create.custom.hatch.error.hatch.executable.path.is.not.valid",
            hatchExecutablePath)
  )

  class HatchExecutionFailure(execError: ExecError) : HatchUIError(
    message("sdk.create.custom.hatch.error.execution.failed",
            execError.command.joinToString(" ")
    )
  )
}

internal fun String.toPath(): PyResult<Path> {
  return when (val selectedPath = Path.of(this)) {
    null -> Result.failure(HatchUIError.HatchExecutablePathIsNotValid(this))
    else -> Result.success(selectedPath)
  }
}

private class HatchEnvComboBoxListCellRenderer : ColoredListCellRenderer<HatchVirtualEnvironment>() {
  override fun customizeCellRenderer(list: JList<out HatchVirtualEnvironment?>, value: HatchVirtualEnvironment?, index: Int, selected: Boolean, hasFocus: Boolean) {
    if (value == null) {
      icon = AnimatedIcon.Default.INSTANCE
      append(message("sdk.create.custom.hatch.environment.loading"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      return
    }

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

private fun Panel.addEnvironmentComboBox(
  model: PythonMutableTargetAddInterpreterModel,
  hatchEnvironmentProperty: ObservableMutableProperty<HatchVirtualEnvironment?>,
  propertyGraph: PropertyGraph,
  validationRequestor: DialogValidationRequestor,
  isValidateOnlyNotExisting: Boolean,
): ComboBox<HatchVirtualEnvironment> {
  val environmentAlreadyExists = propertyGraph.property(false)
  lateinit var environmentComboBox: ComboBox<HatchVirtualEnvironment>

  row(message("sdk.create.custom.hatch.environment")) {
    environmentComboBox = comboBox(emptyList(), HatchEnvComboBoxListCellRenderer())
      .bindItem(hatchEnvironmentProperty)
      .displayLoaderWhen(model.hatchEnvironmentsLoading, scope = model.scope, uiContext = model.uiContext)
      .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(hatchEnvironmentProperty))
      .validationInfo { component ->
        environmentAlreadyExists.set(false)
        with(component) {
          when {
            !isVisible -> null
            item == null -> ValidationInfo(message("sdk.create.custom.hatch.error.no.environments.to.select"))
            isValidateOnlyNotExisting && item?.pythonVirtualEnvironment is PythonVirtualEnvironment.Existing -> {
              environmentAlreadyExists.set(true)
              ValidationInfo(message("sdk.create.custom.hatch.environment.exists"))
            }
            else -> null
          }
        }
      }
      .align(Align.FILL)
      .component
  }

  row("") {
    validationTooltip(
      message = message("sdk.create.custom.hatch.environment.exists"),
      firstActionLink = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
        PythonNewProjectWizardCollector.logExistingVenvFixUsed()
        model.state.selectedHatchEnv.set(environmentComboBox.item)
        model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PythonSupportedEnvironmentManagers.HATCH)
      },
      validationType = ValidationType.ERROR
    ).align(Align.FILL)
  }.visibleIf(environmentAlreadyExists)
  return environmentComboBox
}

private fun Panel.addExecutableSelector(
  model: PythonMutableTargetAddInterpreterModel,
  propertyGraph: PropertyGraph,
  hatchExecutableProperty: ObservableMutableProperty<String>,
  hatchErrorProperty: ObservableMutableProperty<PyError?>,
  validationRequestor: DialogValidationRequestor,
  installHatchActionLink: ActionLink? = null,
) {
  val hatchErrorMessage = propertyGraph.property<@Nls String>("")
  propertyGraph.dependsOn(hatchErrorMessage, hatchErrorProperty, deleteWhenChildModified = false) {
    when (val error = hatchErrorProperty.get()) {
      null -> ""
      is MessageError -> error.message
      is ExecError -> HatchUIError.HatchExecutionFailure(error).message
    }
  }

  executableSelector(
    hatchExecutableProperty,
    validationRequestor,
    message("sdk.create.custom.venv.executable.path", "hatch"),
    message("sdk.create.custom.venv.missing.text", "hatch"),
    installHatchActionLink
  ).validationOnInput { selector ->
    if (!selector.isVisible) return@validationOnInput null

    if (hatchExecutableProperty.get() != model.state.hatchExecutable.get()) {
      model.state.hatchExecutable.set(hatchExecutableProperty.get())
    }
    null
  }

  row("") {
    validationTooltip(textProperty = hatchErrorMessage, validationType = ValidationType.ERROR).align(Align.FILL)
  }.visibleIf(object : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      hatchErrorProperty.afterChange { listener(invoke()) }
    }

    override fun invoke(): Boolean = hatchErrorProperty.get() != null
  })
}


internal fun Panel.buildHatchFormFields(
  model: PythonMutableTargetAddInterpreterModel,
  hatchEnvironmentProperty: ObservableMutableProperty<HatchVirtualEnvironment?>,
  hatchExecutableProperty: ObservableMutableProperty<String>,
  propertyGraph: PropertyGraph,
  validationRequestor: DialogValidationRequestor,
  isGenerateNewMode: Boolean = false,
  installHatchActionLink: ActionLink? = null,
  basePythonComboboxReceiver: ((PythonInterpreterComboBox) -> Unit) = { },
) {
  val environmentComboBox = addEnvironmentComboBox(
    model = model,
    hatchEnvironmentProperty = hatchEnvironmentProperty,
    propertyGraph = propertyGraph,
    validationRequestor = validationRequestor,
    isValidateOnlyNotExisting = isGenerateNewMode
  )

  if (isGenerateNewMode) {
    row(message("sdk.create.custom.base.python")) {
      val basePythonComboBox = pythonInterpreterComboBox(
        selectedSdkProperty = model.state.baseInterpreter,
        model = model,
        onPathSelected = model::addInterpreter,
        busyState = model.interpreterLoading
      ).align(Align.FILL).component
      basePythonComboboxReceiver(basePythonComboBox)
    }
  }

  val hatchError = propertyGraph.property<PyError?>(null)
  addExecutableSelector(model, propertyGraph, hatchExecutableProperty, hatchError, validationRequestor, installHatchActionLink)

  model.hatchEnvironmentsResult.onEach { environmentsResult ->
    environmentsResult?.let {
      environmentComboBox.syncWithEnvs(it, isFilterOnlyExisting = !isGenerateNewMode)
      if (it.isFailure) hatchEnvironmentProperty.set(null)
    }
    hatchError.set((environmentsResult as? Result.Failure)?.error)
  }.launchIn(model.scope)
}

@Synchronized
private fun ComboBox<HatchVirtualEnvironment>.syncWithEnvs(
  environmentsResult: PyResult<List<HatchVirtualEnvironment>>,
  isFilterOnlyExisting: Boolean = false,
) {
  removeAllItems()
  val environments = environmentsResult.getOr { return }
  environments.filter { !isFilterOnlyExisting || it.pythonVirtualEnvironment is PythonVirtualEnvironment.Existing }.forEach {
    addItem(it)
  }
}


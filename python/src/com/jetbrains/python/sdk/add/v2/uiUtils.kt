// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.ui.util.preferredHeight
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.CREATE_NEW
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.CUSTOM
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.VIRTUALENV
import com.jetbrains.python.sdk.conda.CondaInstallManager
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.nio.file.Paths
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


internal fun <T> PropertyGraph.booleanProperty(dependency: ObservableProperty<T>, value: T) =
  lazyProperty { dependency.get() == value }.apply { dependsOn(dependency) { dependency.get() == value } }

class PythonNewEnvironmentDialogNavigator {
  var selectionMode: ObservableMutableProperty<PythonInterpreterSelectionMode>? = null
  lateinit var selectionMethod: ObservableMutableProperty<PythonInterpreterSelectionMethod>
  lateinit var newEnvManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers>
  lateinit var existingEnvManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers>

  fun navigateTo(
    newMode: PythonInterpreterSelectionMode? = null,
    newMethod: PythonInterpreterSelectionMethod? = null,
    newManager: PythonSupportedEnvironmentManagers? = null,
  ) {
    newMode?.let { selectionMode?.set(it) }
    newMethod?.let { method ->
      selectionMethod.set(method)
    }
    newManager?.let {
      when (newMethod) {
        CREATE_NEW -> newEnvManager.set(it)
        SELECT_EXISTING -> existingEnvManager.set(it)
        null -> null
      }
    }
  }

  // todo think about whether i need to save state in regular dialog
  fun saveLastState() {
    val properties = PropertiesComponent.getInstance()

    val mode = selectionMode?.let {
      val mode = selectionMode!!.get()
      properties.setValue(FAV_MODE, it.get().toString())
      mode
    } ?: VIRTUALENV

    if (mode == CUSTOM) {
      val method = selectionMethod.get()
      val manager = if (method == CREATE_NEW) newEnvManager.get() else existingEnvManager.get()
      properties.setValue(FAV_METHOD, method.toString())
      properties.setValue(FAV_MANAGER, manager.toString())
    }
    else {
      // restore defaults
      properties.setValue(FAV_METHOD, CREATE_NEW.toString())
      properties.setValue(FAV_MANAGER, VIRTUALENV.toString())
    }
  }


  /**
   * Loads all fields from storage ([selectionMode] is only loaded when included into `onlyAllowedSelectionModes`)
   */
  internal fun restoreLastState(allowedInterpreterTypes: Collection<PythonInterpreterSelectionMode>) {
    val properties = PropertiesComponent.getInstance()

    val modeString = properties.getValue(FAV_MODE) ?: return
    val mode = PythonInterpreterSelectionMode.valueOf(modeString)
    if (mode !in allowedInterpreterTypes) return
    selectionMode?.set(mode)

    if (mode == CUSTOM) {
      val method = PythonInterpreterSelectionMethod.valueOf(properties.getValue(FAV_METHOD) ?: return)
      selectionMethod.set(method)

      val manager = PythonSupportedEnvironmentManagers.valueOf(properties.getValue(FAV_MANAGER) ?: return)
      if (method == CREATE_NEW) newEnvManager.set(manager) else existingEnvManager.set(manager)
    }
  }

  companion object {
    const val FAV_MODE = "python.new.interpreter.fav.mode"
    const val FAV_METHOD = "python.new.interpreter.fav.method"
    const val FAV_MANAGER = "python.new.interpreter.fav.manager"
  }
}


internal fun SimpleColoredComponent.customizeForPythonInterpreter(interpreter: PythonSelectableInterpreter) {
  when (interpreter) {
    is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> {
      icon = IconLoader.getTransparentIcon(PythonPsiApiIcons.Python)
      append(interpreter.homePath)
      append(" " + message("sdk.rendering.detected.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is InstallableSelectableInterpreter -> {
      icon = AllIcons.Actions.Download
      append(interpreter.sdk.name)
      append(" " + message("sdk.rendering.installable.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is ExistingSelectableInterpreter -> {
      icon = PythonPsiApiIcons.Python
      append(interpreter.sdk.versionString!!)
      append(" " + interpreter.homePath, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is InterpreterSeparator -> return
    else -> error("Unknown PythonSelectableInterpreter type")
  }
}


class PythonSdkComboBoxListCellRenderer : ColoredListCellRenderer<Any>() {

  override fun getListCellRendererComponent(list: JList<out Any>?, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean): Component {
    if (value is InterpreterSeparator) return TitledSeparator(value.text).apply { setLabelFocusable(false) }
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    if (value is PythonSelectableInterpreter) customizeForPythonInterpreter(value)
  }
}

class CondaEnvComboBoxListCellRenderer : ColoredListCellRenderer<PyCondaEnv>() {
  @Suppress("HardCodedStringLiteral")
  override fun customizeCellRenderer(list: JList<out PyCondaEnv>, value: PyCondaEnv?, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (val identity = value?.envIdentity) {
      is PyCondaEnvIdentity.NamedEnv -> append(identity.userReadableName)
      is PyCondaEnvIdentity.UnnamedEnv -> append(identity.envPath)
      else -> append("")
    }
  }
}


class PythonEnvironmentComboBoxRenderer : ColoredListCellRenderer<Any>() {
  override fun customizeCellRenderer(list: JList<out Any>, value: Any, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (value) {
      is PythonSupportedEnvironmentManagers -> {
        icon = value.icon
        append(message(value.nameKey))
      }
      is PythonInterpreterCreationTargets -> {
        icon = value.icon
        append(message(value.nameKey))
      }
    }

  }
}

internal fun Row.pythonInterpreterComboBox(
  selectedSdkProperty: ObservableMutableProperty<PythonSelectableInterpreter?>, // todo not sdk
  model: PythonAddInterpreterModel,
  onPathSelected: (String) -> Unit, busyState: StateFlow<Boolean>? = null,
): Cell<PythonInterpreterComboBox> {

  val comboBox = PythonInterpreterComboBox(selectedSdkProperty, model, onPathSelected)
  val cell = cell(comboBox)
    .bindItem(selectedSdkProperty)
    .applyToComponent {
      preferredHeight = 30
      isEditable = true
    }

  model.scope.launch(model.uiContext, start = CoroutineStart.UNDISPATCHED) {
    busyState?.collectLatest { currentValue ->
      withContext(model.uiContext) {
        comboBox.setBusy(currentValue)
        if (currentValue) {
          // todo disable cell
        }
      }
    }
  }
  return cell


}

class PythonInterpreterComboBox(
  val backingProperty: ObservableMutableProperty<PythonSelectableInterpreter?>,
  val controller: PythonAddInterpreterModel,
  val onPathSelected: (String) -> Unit,
) : ComboBox<PythonSelectableInterpreter?>() {

  private lateinit var itemsFlow: StateFlow<List<PythonSelectableInterpreter>>
  val items: List<PythonSelectableInterpreter>
    get() = itemsFlow.value

  private val interpreterToSelect = controller.propertyGraph.property<String?>(null)

  init {
    renderer = PythonSdkComboBoxListCellRenderer()
    val newOnPathSelected: (String) -> Unit = {
      interpreterToSelect.set(it)
      onPathSelected(it)
    }
    editor = PythonSdkComboBoxWithBrowseButtonEditor(this, controller, newOnPathSelected)

  }

  fun setItems(flow: StateFlow<List<PythonSelectableInterpreter>>) {
    itemsFlow = flow
    controller.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      flow.collectLatest { interpreters ->
        withContext(controller.uiContext) {
          with(this@PythonInterpreterComboBox) {
            val currentlySelected = selectedItem as PythonSelectableInterpreter?
            removeAllItems()
            interpreters.forEach(this::addItem)

            val newPath = interpreterToSelect.get()
            val newValue = if (newPath != null) {
              val newItem = interpreters.find { it.homePath == newPath }
              if (newItem == null) error("path but no item")
              interpreterToSelect.set(null)
              newItem
            }
            else if (currentlySelected == null || currentlySelected !in interpreters) {
              interpreters.firstOrNull() // todo is there better fallback value?
            }
            else {
              currentlySelected
            }


            //val newValue = if (newPath != null) {
            //  val newItem = interpreters.find { it.homePath == newPath }
            //  newPath = null
            //  newItem ?: currentlySelected
            //} else currentlySelected


            backingProperty.set(newValue) // todo do I even need to set it?
          }

        }
      }
    }
  }

  fun setBusy(busy: Boolean) {
    (editor as PythonSdkComboBoxWithBrowseButtonEditor).setBusy(busy)
  }
}

/**
 * Note. Here [ExtendableTextComponent.Extension] is used to display animated loader icon. This approach requires [ExtendableTextComponent]
 * to be rendered as the combobox context. When a [ComboBox] is configured as non-editable then an [ComboBox.renderer] is used to render the
 * selected item. And rendering [ComboBox] with [javax.swing.ListCellRenderer] does not support displaying animations. To work this problem
 * around [ComboBox] is made "editable" (and "disabled", what prevents changes) while displaying loader icon, and returned to its initial
 * state afterward.
 *
 * @param makeTemporaryEditable if the property is set then [this] ComboBox is made temporary editable while displaying
 *                              animated loader icon
 */
private fun ComboBox<*>.displayLoaderWhen(
  loading: SharedFlow<Boolean>,
  makeTemporaryEditable: Boolean = false,
  scope: CoroutineScope,
  uiContext: CoroutineContext,
) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loading.collectLatest { currentValue ->
      withContext(uiContext) {
        if (currentValue) displayLoader(makeTemporaryEditable) else hideLoader(makeTemporaryEditable)
      }
    }
  }
}

internal fun <T : TextFieldWithBrowseButton> Cell<T>.displayLoaderWhen(
  loading: StateFlow<Boolean>,
  scope: CoroutineScope,
  uiContext: CoroutineContext,
): Cell<T> =
  applyToComponent { displayLoaderWhen(loading, scope, uiContext) }

internal fun <T, C : ComboBox<T>> Cell<C>.displayLoaderWhen(
  loading: SharedFlow<Boolean>,
  makeTemporaryEditable: Boolean = false,
  scope: CoroutineScope,
  uiContext: CoroutineContext,
): Cell<C> =
  applyToComponent {
    if (makeTemporaryEditable && editor.editorComponent !is ExtendableTextField) {
      editor = object : BasicComboBoxEditor() {
        override fun createEditorComponent(): JTextField = ExtendableTextField().apply { border = null }
      }
    }
    displayLoaderWhen(loading, makeTemporaryEditable, scope, uiContext)
  }

private fun TextFieldWithBrowseButton.displayLoaderWhen(loading: SharedFlow<Boolean>, scope: CoroutineScope, uiContext: CoroutineContext) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loading.collectLatest { currentValue ->
      withContext(uiContext) {
        if (currentValue) displayLoader() else hideLoader()
      }
    }
  }
}

private fun ComboBox<*>.displayLoader(makeTemporaryEditable: Boolean) {
  if (makeTemporaryEditable) {
    isEditable = true
  }
  isEnabled = false
  (editor.editorComponent as? ExtendableTextComponent)?.installLoadingExtension()
}

private fun ComboBox<*>.hideLoader(restoreNonEditableState: Boolean) {
  if (restoreNonEditableState) {
    isEditable = false
  }
  isEnabled = true
  (editor.editorComponent as? ExtendableTextComponent)?.removeLoadingExtension()
}

private fun TextFieldWithBrowseButton.displayLoader() {
  isEnabled = false
  (childComponent as? ExtendableTextComponent)?.installLoadingExtension()
}

private fun TextFieldWithBrowseButton.hideLoader() {
  isEnabled = true
  (childComponent as? ExtendableTextComponent)?.removeLoadingExtension()
}

private val loaderExtension = ExtendableTextComponent.Extension.create(AnimatedIcon.Default.INSTANCE, null, null)

private fun ExtendableTextComponent.installLoadingExtension() {
  addExtension(loaderExtension)
}

private fun ExtendableTextComponent.removeLoadingExtension() {
  removeExtension(loaderExtension)
}

const val UNKNOWN_EXECUTABLE = "<unknown_executable>"

fun Panel.executableSelector(
  executable: ObservableMutableProperty<String>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String,
  installAction: ActionLink? = null,
): Cell<TextFieldWithBrowseButton> {
  var textFieldCell: Cell<TextFieldWithBrowseButton>? = null
  var validationPanel: JPanel? = null

  val selectExecutableLink = ActionLink(message("sdk.create.custom.select.executable.link")) {
    FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(), null, null) {
      executable.set(it.path)
    }
  }

  val (firstFix, secondFix) = if (installAction == null) Pair(selectExecutableLink, null) else Pair(installAction, selectExecutableLink)

  row("") {
    validationPanel = validationTooltip(missingExecutableText,
                                        firstFix,
                                        secondFix,
                                        validationType = ValidationType.WARNING,
                                        inline = true)
      .align(Align.FILL)
      .component
  }.visibleIf(executable.equalsTo(UNKNOWN_EXECUTABLE))

  row(labelText) {
    textFieldCell = textFieldWithBrowseButton()
      .bindText(executable)
      .align(AlignX.FILL)
      .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(executable))
      .validationOnInput {
        if (it.isVisible) {
          val path = Paths.get(it.text)
          when {
            it.text.isEmpty() -> error(message("sdk.create.not.executable.empty.error"))
            !path.exists() -> error(message("sdk.create.not.executable.does.not.exist.error"))
            path.isDirectory() -> error(message("sdk.create.executable.directory.error"))
            else -> null
          }
        }
        else if (validationPanel!!.isVisible && it.text == UNKNOWN_EXECUTABLE) {
          error(message("sdk.create.not.executable.does.not.exist.error"))
        }
        else null
      }
  }.visibleIf(executable.notEqualsTo(UNKNOWN_EXECUTABLE))

  return textFieldCell!!
}

internal fun createInstallCondaFix(model: PythonAddInterpreterModel): ActionLink {
  return ActionLink(message("sdk.create.conda.install.fix")) {
    PythonSdkFlavor.clearExecutablesCache()
    CondaInstallManager.installLatest(null)
    model.scope.launch(model.uiContext) {
      model.condaEnvironmentsLoading.value = true
      model.detectCondaExecutable()
      model.detectCondaEnvironments()
      model.condaEnvironmentsLoading.value = false
    }
  }
}



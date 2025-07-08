// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.impl.installer.CondaInstallManager
import com.intellij.python.community.services.shared.VanillaPythonWithLanguageLevel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.ui.util.preferredHeight
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.CREATE_NEW
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.CUSTOM
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.VIRTUALENV
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
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


internal fun SimpleColoredComponent.customizeForPythonInterpreter(isLoading: Boolean, interpreter: PythonSelectableInterpreter?) {
  when {
    isLoading -> {
      append(message("sdk.create.custom.hatch.environment.loading"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      return
    }
    interpreter == null -> {
      icon = AllIcons.General.ShowWarning
      append(message("sdk.create.custom.existing.error.no.interpreters.to.select"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      return
    }
  }

  when (interpreter) {
    is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> {
      icon = IconLoader.getTransparentIcon(interpreter.ui?.icon ?: PythonPsiApiIcons.Python)
      val title = interpreter.ui?.title ?: message("sdk.rendering.detected.grey.text")
      append(String.format("Python %-4s", interpreter.languageLevel))
      append(" (" + replaceHomePathToTilde(interpreter.homePath) + ") $title", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is InstallableSelectableInterpreter -> {
      icon = AllIcons.Actions.Download
      append(interpreter.sdk.name)
      append(" " + message("sdk.rendering.installable.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is ExistingSelectableInterpreter -> {
      icon = PythonPsiApiIcons.Python
      // This is a dirty hack, but version string might be null for invalid pythons
      // We must fix it after PythonInterpreterService will make sdk needless
      append(interpreter.sdk.versionString ?: "broken interpreter")
      append(" " + replaceHomePathToTilde(interpreter.homePath), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }
}

private val userHomePath = lazy {
  try {
    Path(SystemProperties.getUserHome()).normalize()
  }
  catch (_: InvalidPathException) {
    null
  }
}

/**
 * Replaces [userHomePath] in  [sdkHomePath] to `~`
 */
@ApiStatus.Internal
fun replaceHomePathToTilde(sdkHomePath: @NonNls String): @NlsSafe String {
  try {
    val path = Path(sdkHomePath.trim()).normalize()
    userHomePath.value?.let { homePath ->
      if (path.startsWith(homePath)) {
        return "~${homePath.fileSystem.separator}" + homePath.relativize(path).normalize().toString()
      }
    }
    return path.toString()
  }
  catch (_: InvalidPathException) {
    return sdkHomePath.trim()
  }
}


class PythonSdkComboBoxListCellRenderer(val loadingFlow: StateFlow<Boolean>) : ColoredListCellRenderer<PythonSelectableInterpreter?>() {

  override fun getListCellRendererComponent(list: JList<out PythonSelectableInterpreter?>?, value: PythonSelectableInterpreter?, index: Int, selected: Boolean, hasFocus: Boolean): Component {
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(list: JList<out PythonSelectableInterpreter?>, value: PythonSelectableInterpreter?, index: Int, selected: Boolean, hasFocus: Boolean) {
    customizeForPythonInterpreter(loadingFlow.value, value)
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

internal fun Panel.pythonInterpreterComboBox(
  title: @Nls String,
  selectedSdkProperty: ObservableMutableProperty<PythonSelectableInterpreter?>, // todo not sdk
  model: PythonAddInterpreterModel,
  validationRequestor: DialogValidationRequestor,
  onPathSelected: (VanillaPythonWithLanguageLevel) -> Unit,
  customizer: RowsRange.() -> Unit = {},
): PythonInterpreterComboBox {
  val comboBox = PythonInterpreterComboBox(model, onPathSelected, ShowingMessageErrorSync)
    .apply {
      setBusy(true)
    }

  rowsRange {
    row(title) {
      cell(comboBox)
        .bindItem(selectedSdkProperty)
        .applyToComponent {
          preferredHeight = JBUI.scale(30)
          isEditable = true
        }
        .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(selectedSdkProperty))
        .validationInfo {
          if (comboBox.isVisible && !comboBox.isBusy && selectedSdkProperty.get() == null) {
            ValidationInfo(message("sdk.create.custom.existing.error.no.interpreters.to.select"))
          }
          else null
        }
        .validationOnApply {
          if (!comboBox.isVisible) return@validationOnApply null
          // This component must set sdk: clients expect it not to be null (PY-77463)
          if (comboBox.isBusy || selectedSdkProperty.get() == null) {
            ValidationInfo(message("python.add.sdk.panel.wait"))
          }
          else null
        }
        .align(Align.FILL)
    }
  }.also { customizer(it) }

  return comboBox
}

internal class PythonInterpreterComboBox(
  val controller: PythonAddInterpreterModel,
  val onPathSelected: (VanillaPythonWithLanguageLevel) -> Unit,
  private val errorSink: ErrorSink,
) : ComboBox<PythonSelectableInterpreter?>() {

  init {
    renderer = PythonSdkComboBoxListCellRenderer(controller.interpreterLoading)
    val newOnPathSelected: (String) -> Unit = {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), message("python.sdk.validating.environment")) {
        controller.getSystemPythonFromSelection(it, errorSink)?.let { python ->
          onPathSelected(python)
        }
      }
    }
    editor = PythonSdkComboBoxWithBrowseButtonEditor(this, controller, newOnPathSelected)
  }

  fun initialize(scope: CoroutineScope, flow: Flow<List<PythonSelectableInterpreter>>) {
    controller.interpreterLoading.onEach {
      setBusy(it)
    }.launchIn(scope + Dispatchers.EDT)


    flow.onEach { interpreters ->
      removeAllItems()
      interpreters.forEach(this::addItem)
    }.launchIn(scope + Dispatchers.EDT)
  }

  // Both these methods are abstraction leakage and should be rewritten

  fun setBusy(busy: Boolean) {
    (editor as PythonSdkComboBoxWithBrowseButtonEditor).setBusy(busy)
  }

  val isBusy: Boolean get() = (editor as PythonSdkComboBoxWithBrowseButtonEditor).isBusy
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
internal fun ComboBox<*>.displayLoaderWhen(
  loading: Flow<Boolean>,
  makeTemporaryEditable: Boolean = false,
  scope: CoroutineScope,
) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loading.collectLatest { currentValue ->
      withContext(Dispatchers.EDT) {
        if (currentValue) displayLoader(makeTemporaryEditable) else hideLoader(makeTemporaryEditable)
      }
    }
  }
}

internal fun <T, C : ComboBox<T>> Cell<C>.withExtendableTextFieldEditor(): Cell<C> =
  applyToComponent {
    if (editor.editorComponent !is ExtendableTextField) {
      editor = object : BasicComboBoxEditor() {
        override fun createEditorComponent(): JTextField = ExtendableTextField().apply { border = null }
      }
    }
  }

internal fun TextFieldWithBrowseButton.displayLoaderWhen(loading: SharedFlow<Boolean>, scope: CoroutineScope) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loading.collectLatest { currentValue ->
      withContext(Dispatchers.EDT) {
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

const val UNKNOWN_EXECUTABLE: String = "<unknown_executable>"

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
  }.visibleIf(executable.equalsTo(UNKNOWN_EXECUTABLE)).visibleIf(executable.equalsTo(""))

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

internal fun createInstallCondaFix(model: PythonAddInterpreterModel, errorSink: ErrorSink): ActionLink {
  return ActionLink(message("sdk.create.custom.venv.install.fix.title", "Miniconda", "")) {
    PythonSdkFlavor.clearExecutablesCache()
    CondaInstallManager.installLatest(null)
    runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.progress.title.detect.executable")) {
      model.condaEnvironmentsLoading.value = true
      model.detectCondaExecutable()
      model.detectCondaEnvironmentsOrError(errorSink)
      model.condaEnvironmentsLoading.value = false
    }
  }
}



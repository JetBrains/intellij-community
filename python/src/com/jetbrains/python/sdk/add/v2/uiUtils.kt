// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.impl.installer.CondaInstallManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.onFailure
import com.jetbrains.python.parser.icons.PythonParserIcons
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.CREATE_NEW
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.CUSTOM
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.VIRTUALENV
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.nio.file.InvalidPathException
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor
import kotlin.io.path.Path


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


internal fun <P : PathHolder> SimpleColoredComponent.customizeForPythonInterpreter(isLoading: Boolean, interpreter: PythonSelectableInterpreter<P>?) {
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
      icon = IconLoader.getTransparentIcon(interpreter.ui?.icon ?: PythonParserIcons.PythonFile)
      val title = interpreter.ui?.toolName ?: message("sdk.rendering.detected.grey.text")
      append(String.format("Python %-4s", interpreter.pythonInfo.languageLevel))
      append(" (" + replaceHomePathToTilde(interpreter.homePath.toString()) + ") $title", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is InstallableSelectableInterpreter -> {
      icon = AllIcons.Actions.Download
      append(interpreter.sdk.name)
      append(" " + message("sdk.rendering.installable.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is ExistingSelectableInterpreter -> {
      icon = PythonParserIcons.PythonFile
      // This is a dirty hack, but version string might be null for invalid pythons
      // We must fix it after PythonInterpreterService will make sdk needless
      append(interpreter.sdkWrapper.sdk.versionString ?: "broken interpreter")
      append(" " + replaceHomePathToTilde(interpreter.homePath.toString()), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
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


class PythonSdkComboBoxListCellRenderer<P : PathHolder>(val isLoading: () -> Boolean) : ColoredListCellRenderer<PythonSelectableInterpreter<P>?>() {

  override fun getListCellRendererComponent(list: JList<out PythonSelectableInterpreter<P>?>?, value: PythonSelectableInterpreter<P>?, index: Int, selected: Boolean, hasFocus: Boolean): Component {
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(list: JList<out PythonSelectableInterpreter<P>?>, value: PythonSelectableInterpreter<P>?, index: Int, selected: Boolean, hasFocus: Boolean) {
    customizeForPythonInterpreter(isLoading.invoke(), value)
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

internal fun <P : PathHolder> Panel.pythonInterpreterComboBox(
  fileSystem: FileSystem<P>,
  title: @Nls String,
  selectedSdkProperty: ObservableMutableProperty<PythonSelectableInterpreter<P>?>, // todo not sdk
  validationRequestor: DialogValidationRequestor,
  onPathSelected: suspend (P) -> PyResult<PythonSelectableInterpreter<P>>,
  customizer: RowsRange.() -> Unit = {},
): PythonInterpreterComboBox<P> {
  val comboBox = PythonInterpreterComboBox(onPathSelected, fileSystem, ShowingMessageErrorSync)
    .apply {
      setBusy(true)
    }

  rowsRange {
    row(title) {
      cell(comboBox)
        .bindItem(selectedSdkProperty)
        .applyToComponent {
          preferredSize = JBUI.size(preferredSize)
          isEditable = true
        }
        .validationRequestor(
          validationRequestor
            and WHEN_PROPERTY_CHANGED(selectedSdkProperty)
            and WHEN_PROPERTY_CHANGED(comboBox.isLoading)
        )
        .validationInfo {
          when {
            !it.isVisible -> null
            it.isLoading.get() -> ValidationInfo(message("python.add.sdk.panel.wait"))
            selectedSdkProperty.get() == null -> ValidationInfo(message("sdk.create.custom.existing.error.no.interpreters.to.select"))
            else -> null
          }
        }
        .align(Align.FILL)
    }
  }.also { customizer(it) }

  return comboBox
}

internal class PythonInterpreterComboBox<P : PathHolder>(
  val onPathSelected: suspend (P) -> PyResult<PythonSelectableInterpreter<P>>,
  val fileSystem: FileSystem<P>,
  private val errorSink: ErrorSink,
) : ComboBox<PythonSelectableInterpreter<P>?>() {
  val isLoading: ObservableMutableProperty<Boolean> = AtomicBooleanProperty(true)

  init {
    renderer = PythonSdkComboBoxListCellRenderer { isLoading.get() }
    val newOnPathSelected: (String) -> Unit = { rawPath ->
      runWithModalProgressBlocking(ModalTaskOwner.guess(), message("python.sdk.validating.environment")) {
        val pathOnFileSystem = fileSystem.parsePath(rawPath).onFailure { error ->
          errorSink.emit(error)
        }.successOrNull

        val interpreter = pathOnFileSystem?.let {
          onPathSelected(it).onFailure { error -> errorSink.emit(error) }.successOrNull
        }

        interpreter?.let { interpreter ->
          require(isEditable) {
            "works only with editable combobox because it doesn't reject non-listed items (the list will be updated later via coroutine)"
          }
          selectedItem = interpreter
        }
      }
    }
    editor = ComboBoxWithBrowseButtonEditor(this, fileSystem, PyBundle.message("sdk.select.path"), newOnPathSelected)
  }

  fun initialize(scope: CoroutineScope, flow: Flow<List<PythonSelectableInterpreter<P>>?>) {
    flow.onEach { interpreters ->
      if (interpreters == null) {
        setBusy(true)
        return@onEach
      }

      val selectedItemReminder = selectedItem
      removeAllItems()
      interpreters.forEach(this::addItem)
      selectedItemReminder?.let { selectedItem = it }

      setBusy(false)
      isLoading.set(false)
    }.launchIn(scope + Dispatchers.EDT)
  }

  // Both these methods are abstraction leakage and should be rewritten

  fun setBusy(busy: Boolean) {
    (editor as ComboBoxWithBrowseButtonEditor<*, P>).setBusy(busy)
  }

  val isBusy: Boolean get() = (editor as ComboBoxWithBrowseButtonEditor<*, P>).isBusy
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

internal fun JComponent.displayLoaderWhen(loading: SharedFlow<Boolean>, scope: CoroutineScope) {
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

private fun JComponent.displayLoader() {
  isEnabled = false
}

private fun JComponent.hideLoader() {
  isEnabled = true
}

private val loaderExtension = ExtendableTextComponent.Extension.create(AnimatedIcon.Default.INSTANCE, null, null)

private fun ExtendableTextComponent.installLoadingExtension() {
  addExtension(loaderExtension)
}

private fun ExtendableTextComponent.removeLoadingExtension() {
  removeExtension(loaderExtension)
}

internal fun <P : PathHolder> createInstallCondaFix(model: PythonAddInterpreterModel<P>): ActionLink? {
  if ((model.fileSystem as? FileSystem.Eel)?.eelApi != localEel) return null

  return ActionLink(message("sdk.create.custom.venv.install.fix.title", "Miniconda", "")) {
    PythonSdkFlavor.clearExecutablesCache()
    CondaInstallManager.installLatest(null)
    runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.venv.progress.title.detect.executable")) {
      model.condaViewModel.toolValidator.autodetectExecutable()
    }
  }
}



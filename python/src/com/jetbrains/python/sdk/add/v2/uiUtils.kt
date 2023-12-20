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
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
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
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.PySdkToInstall
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.CREATE_NEW
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.CUSTOM
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.VIRTUALENV
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import icons.PythonIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Paths
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


internal fun <T> PropertyGraph.booleanProperty(dependency: ObservableProperty<T>, value: T) =
  lazyProperty { false }.apply { dependsOn(dependency) { dependency.get() == value } }

class PythonNewEnvironmentDialogNavigator {
  lateinit var selectionMode: ObservableMutableProperty<PythonInterpreterSelectionMode>
  lateinit var selectionMethod: ObservableMutableProperty<PythonInterpreterSelectionMethod>
  lateinit var newEnvManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers>
  lateinit var existingEnvManager: ObservableMutableProperty<PythonSupportedEnvironmentManagers>

  fun navigateTo(newMode: PythonInterpreterSelectionMode? = null,
                 newMethod: PythonInterpreterSelectionMethod? = null,
                 newManager: PythonSupportedEnvironmentManagers? = null) {
    newMode?.let { selectionMode.set(it) }
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

  fun saveLastState() {
    val properties = PropertiesComponent.getInstance()

    val mode = selectionMode.get()
    properties.setValue(FAV_MODE, mode.toString())
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


  fun restoreLastState() {
    val properties = PropertiesComponent.getInstance()

    val modeString = properties.getValue(FAV_MODE) ?: return
    val mode = PythonInterpreterSelectionMode.valueOf(modeString)
    selectionMode.set(mode)

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

internal fun SimpleColoredComponent.customizeForPythonSdk(sdk: Sdk) {
  when (sdk) {
    is PyDetectedSdk -> {
      icon = IconLoader.getTransparentIcon(PythonIcons.Python.Python)
      append(sdk.homePath!!)
      append(" " + message("sdk.rendering.detected.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    is PySdkToInstall -> {
      icon = AllIcons.Actions.Download
      append(sdk.name)
      append(" " + message("sdk.rendering.installable.grey.text"), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    else -> {
      icon = PythonIcons.Python.Python
      append(sdk.versionString!!)
      append(" " + sdk.homePath!!, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }
}


class PythonSdkComboBoxListCellRenderer : ColoredListCellRenderer<Any>() {
  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    if (value !is Sdk) error("Not an Sdk")
    customizeForPythonSdk(value)
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

internal fun Row.pythonInterpreterComboBox(selectedSdkProperty: ObservableMutableProperty<Sdk?>,
                                           presenter: PythonAddInterpreterPresenter,
                                           sdksFlow: StateFlow<List<Sdk>>,
                                           onPathSelected: (String) -> Unit): Cell<ComboBox<Sdk?>> =
  comboBox<Sdk?>(emptyList(), PythonSdkComboBoxListCellRenderer())
    .bindItem(selectedSdkProperty)
    .applyToComponent {
      preferredHeight = 30
      isEditable = true
      editor = PythonSdkComboBoxWithBrowseButtonEditor(this, presenter, onPathSelected)

      presenter.scope.launch(start = CoroutineStart.UNDISPATCHED) {
        sdksFlow.collectLatest { sdks ->
          withContext(presenter.uiContext) {
            removeAllItems()
            sdks.forEach(this@applyToComponent::addItem)

            val pathToSelect = tryGetAndRemovePathToSelectAfterModelUpdate() as? String
            val newValue = if (pathToSelect != null) sdks.find { it.homePath == pathToSelect } else findPrioritySdk(sdks)
            selectedSdkProperty.set(newValue)
          }
        }
      }
    }

private fun findPrioritySdk(sdkList: List<Sdk>): Sdk? {
  val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk
  return sdkList.firstOrNull { it.homePath == preferredSdkPath }
         ?: sdkList.firstOrNull { it !is PyDetectedSdk && it !is PySdkToInstall }
         ?: sdkList.firstOrNull { it is PyDetectedSdk }
         ?: sdkList.firstOrNull { it is PySdkToInstall }
}

private val KEY_PATH_TO_SELECT_AFTER_MODEL_UPDATED: Key<String> by lazy { Key.create("PATH_TO_SELECT_AFTER_MODEL_UPDATED") }

internal fun <T> ComboBox<T>.tryGetAndRemovePathToSelectAfterModelUpdate(): @NlsSafe Any? =
  getUserData(KEY_PATH_TO_SELECT_AFTER_MODEL_UPDATED)?.also {
    putUserData(KEY_PATH_TO_SELECT_AFTER_MODEL_UPDATED, null)
  }

internal fun ComboBox<*>.setPathToSelectAfterModelUpdate(targetPath: @NlsSafe String) {
  putUserData(KEY_PATH_TO_SELECT_AFTER_MODEL_UPDATED, targetPath)
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
private fun ComboBox<*>.displayLoaderWhen(loading: SharedFlow<Boolean>,
                                          makeTemporaryEditable: Boolean = false,
                                          scope: CoroutineScope,
                                          uiContext: CoroutineContext) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loading.collectLatest { currentValue ->
      withContext(uiContext) {
        if (currentValue) displayLoader(makeTemporaryEditable) else hideLoader(makeTemporaryEditable)
      }
    }
  }
}

internal fun <T : TextFieldWithBrowseButton> Cell<T>.displayLoaderWhen(loading: StateFlow<Boolean>,
                                                                       scope: CoroutineScope,
                                                                       uiContext: CoroutineContext): Cell<T> =
  applyToComponent { displayLoaderWhen(loading, scope, uiContext) }

internal fun <T, C : ComboBox<T>> Cell<C>.displayLoaderWhen(loading: SharedFlow<Boolean>,
                                                            makeTemporaryEditable: Boolean = false,
                                                            scope: CoroutineScope,
                                                            uiContext: CoroutineContext): Cell<C> =
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

fun Panel.executableSelector(executable: ObservableMutableProperty<String>,
                             validationRequestor: DialogValidationRequestor,
                             labelText: @Nls String,
                             missingExecutableText: @Nls String): Cell<TextFieldWithBrowseButton> {
  var textFieldCell: Cell<TextFieldWithBrowseButton>? = null
  var validationPanel: JPanel? = null

  val selectExecutableLink = ActionLink(message("sdk.create.custom.select.executable.link")) {
    FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(), null, null) {
      executable.set(it.path)
    }
  }

  row("") {
    validationPanel = validationTooltip(missingExecutableText, selectExecutableLink, validationType = ValidationType.WARNING, inline = true)
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



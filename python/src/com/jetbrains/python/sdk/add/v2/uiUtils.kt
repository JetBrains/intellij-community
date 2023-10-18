// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
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
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor


internal fun <T> PropertyGraph.booleanProperty(dependency: ObservableProperty<T>, value: T) =
  lazyProperty { false }.apply { dependsOn(dependency) { dependency.get() == value } }


class PythonSdkComboBoxListCellRenderer : ColoredListCellRenderer<Any>() {
  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (value) {
      is PyDetectedSdk -> append(value.homePath!!)
      is Sdk -> {
        append(value.versionString!!)
        append(" " + value.homePath!!, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
      else -> append("")
    }
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

fun Row.pythonBaseInterpreterComboBox(presenter: PythonAddInterpreterPresenter,
                                      sdksFlow: StateFlow<List<Sdk>>,
                                      sdkSelectedPath: ObservableMutableProperty<String>): ComboBox<String> {
  val component = comboBox<String>(emptyList())
    .bindItem(sdkSelectedPath)
    .align(Align.FILL)
    .component

  val browseExtension = ExtendableTextComponent.Extension.create(AllIcons.General.OpenDisk,
                                                                 AllIcons.General.OpenDiskHover,
                                                                 message("sdk.create.custom.python.browse.tooltip")) {
    val currentBaseSdkPathOnTarget = sdkSelectedPath.get().nullize(nullizeSpaces = true)
    val currentBaseSdkVirtualFile = currentBaseSdkPathOnTarget?.let { presenter.tryGetVirtualFile(it) }
    FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(), null,
                           currentBaseSdkVirtualFile) { file ->
      val nioPath = file?.toNioPath() ?: return@chooseFile
      val targetPath = presenter.getPathOnTarget(nioPath)
      presenter.addAndSelectBaseSdk(targetPath)
    }
  }

  component.isEditable = true
  component.editor = object : BasicComboBoxEditor() {
    override fun createEditorComponent(): JTextField {
      val field = ExtendableTextField()
      field.addExtension(browseExtension)
      field.setBorder(null)
      field.isEditable = false
      field.background = JBUI.CurrentTheme.Arrow.backgroundColor(true, true)
      return field
    }
  }

  presenter.scope.launch(start = CoroutineStart.UNDISPATCHED) {
    sdksFlow.collectLatest { sdks ->
      withContext(presenter.uiContext) {
        component.removeAllItems()
        sdks.map { sdk -> sdk.homePath.orEmpty() }.forEach(component::addItem)
      }
    }
  }

  return component
}


const val UNKNOWN_EXECUTABLE = "<unknown_executable>"

fun Panel.executableSelector(labelText: @Nls String,
                             executable: ObservableMutableProperty<String>,
                             missingExecutableText: @Nls String): TextFieldWithBrowseButton {
  var textFieldComponent: TextFieldWithBrowseButton? = null
  row("") {
    icon(AllIcons.General.Warning)
    text(missingExecutableText) {
      FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(), null, null) {
        executable.set(it.path)
      }
    }
  }.visibleIf(executable.equalsTo(UNKNOWN_EXECUTABLE))

  row(labelText) {
    textFieldComponent = textFieldWithBrowseButton()
      .bindText(executable)
      .align(AlignX.FILL)
      .component
  }.visibleIf(executable.notEqualsTo(UNKNOWN_EXECUTABLE))

  return textFieldComponent!!
}



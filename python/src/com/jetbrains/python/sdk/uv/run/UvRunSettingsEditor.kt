// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.sdkSeemsValid
import com.jetbrains.python.sdk.uv.isUv
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

internal enum class RunTypeField(val string: String) {
  SCRIPT("Script"),
  MODULE("Module"),
  ;

  override fun toString(): String = string
}

internal data class UvRunSettingsEditor(val project: Project) : SettingsEditor<UvRunConfiguration>() {
  private lateinit var panel: JPanel
  private val scriptField = TextFieldWithBrowseButton()
  private val moduleField = JBTextField()
  private val argsField = RawCommandLineEditor().withMonospaced(true)
  private val envField = EnvironmentVariablesTextFieldWithBrowseButton()
  private lateinit var scriptCheckSyncField: JBCheckBox

  private lateinit var uvSdkField: ComboBox<Sdk?>
  private val uvArgsField: RawCommandLineEditor = RawCommandLineEditor().withMonospaced(true)

  private val propertyGraph = PropertyGraph()
  private val isScript = propertyGraph.property(true)
  private val isModule = propertyGraph.property(false)

  constructor(project: Project, uvRunConfiguration: UvRunConfiguration, sdks: List<Sdk?>) : this(project) {
    scriptField.addBrowseFolderListener(
      project,
      FileChooserDescriptorFactory.singleFile(),
    )

    panel = panel {
      var items = listOf(RunTypeField.SCRIPT, RunTypeField.MODULE)

      row(PyBundle.message("uv.run.configuration.editor.field.run")) {
        comboBox(items)
          .gap(RightGap.SMALL)
          .onChanged {
            if (it.selectedItem == RunTypeField.SCRIPT) {
              isScript.set(true)
              isModule.set(false)
              scriptField.text = ""
            }
            else {
              isScript.set(false)
              isModule.set(true)
              moduleField.text = ""
            }
          }
          .component
          .apply {
            selectedItem = when (uvRunConfiguration.options.runType) {
              UvRunType.SCRIPT -> RunTypeField.SCRIPT
              UvRunType.MODULE -> RunTypeField.MODULE
            }
          }
      }

      row(PyBundle.message("uv.run.configuration.editor.field.script")) {
        cell(scriptField)
          .align(AlignX.FILL)
      }.visibleIf(isScript)

      row(PyBundle.message("uv.run.configuration.editor.field.module")) {
        cell(moduleField)
          .align(AlignX.FILL)
      }.visibleIf(isModule)

      row(PyBundle.message("uv.run.configuration.editor.field.arguments")) {
        cell(argsField)
          .align(AlignX.FILL)
      }

      row(PyBundle.message("uv.run.configuration.editor.field.environment")) {
        cell(envField)
          .align(AlignX.FILL)
      }

      row("") {
        scriptCheckSyncField = checkBox(PyBundle.message("uv.run.configuration.editor.field.check.sync"))
          .component
      }

      separator()

      row {
        uvSdkField = comboBox(sdks, PySdkListCellRenderer())
          .columns(COLUMNS_LARGE)
          .align(AlignX.FILL)
          .component

        uvSdkField.addItemListener {
          uvRunConfiguration.options.uvSdkKey = uvSdkField.selectedItem.let { it as? Sdk }?.name
        }

        uvSdkField.selectedItem = uvRunConfiguration.options.uvSdk
      }

      row(PyBundle.message("uv.run.configuration.editor.field.uv.arguments")) {
        cell(uvArgsField)
          .align(AlignX.FILL)
      }
    }
  }

  override fun resetEditorFrom(uvRunConfiguration: UvRunConfiguration) {
    scriptField.text = uvRunConfiguration.options.scriptOrModule
    moduleField.text = uvRunConfiguration.options.scriptOrModule
    argsField.text = uvRunConfiguration.options.args.joinToString(" ")
    envField.data = EnvironmentVariablesData.create(uvRunConfiguration.options.env, true)
    scriptCheckSyncField.isSelected = uvRunConfiguration.options.checkSync
    uvSdkField.selectedItem = uvRunConfiguration.options.uvSdk
    uvArgsField.text = uvRunConfiguration.options.uvArgs.joinToString(" ")
  }

  override fun applyEditorTo(uvRunConfiguration: UvRunConfiguration) {
    uvRunConfiguration.options.runType = if (isScript.get()) {
      uvRunConfiguration.options.scriptOrModule = scriptField.text.trim()
      UvRunType.SCRIPT
    } else {
      uvRunConfiguration.options.scriptOrModule = moduleField.text.trim()
      UvRunType.MODULE
    }
    uvRunConfiguration.options.args = argsField.text.splitParams()
    uvRunConfiguration.options.env = envField.data.envs
    uvRunConfiguration.options.checkSync = scriptCheckSyncField.isSelected
    uvRunConfiguration.options.uvSdkKey = uvSdkField.selectedItem.let {it as? Sdk}?.name
    uvRunConfiguration.options.uvArgs = uvArgsField.text.splitParams()
  }

  override fun createEditor(): JComponent = panel
}

@ApiStatus.Internal
fun uvSdkList(): List<Sdk?> =
  listOf(null) +
  PythonSdkUtil
    .getAllSdks()
    .filter { sdk ->
      sdk.isUv && sdk.sdkSeemsValid && !PythonSdkType.hasInvalidRemoteCredentials(sdk)
    }

private val whiteSpaceRegex = Regex("\\s+")

private fun String.splitParams(): List<String> = this.trim().split(whiteSpaceRegex).filter { it != "" }
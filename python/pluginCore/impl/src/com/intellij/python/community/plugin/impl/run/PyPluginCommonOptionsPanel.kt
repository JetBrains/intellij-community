// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPyCommonOptionsForm
import com.jetbrains.python.sdk.PySdkListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JRadioButton

class PyPluginCommonOptionsPanel {

  lateinit var envsComponent: EnvironmentVariablesComponent
  lateinit var useModuleSdkRadioButton: JRadioButton
  lateinit var moduleComboBox: ModulesComboBox
  lateinit var useSpecifiedSdkRadioButton: JRadioButton
  lateinit var interpreterComboBox: ComboBox<Sdk>
  lateinit var interpreterOptionsTextField: RawCommandLineEditor
  lateinit var workingDirectoryTextField: TextFieldWithBrowseButton
  lateinit var pathMappingsRow: Row
  lateinit var pathMappingsComponent: PathMappingsComponent
  lateinit var addContentRootsCheckbox: JCheckBox
  lateinit var addSourceRootsCheckbox: JCheckBox

  @JvmField
  val panel = panel {
    collapsibleGroup(PyBundle.message("python.sdk.common.options.environment")) {
      row {
        envsComponent = cell(EnvironmentVariablesComponent())
          .label(PyBundle.message("runcfg.labels.environment_variables"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .applyToComponent { remove(label) }
          .component
      }

      buttonsGroup {
        row(PyBundle.message("runcfg.labels.python.interpreter")) {
          useModuleSdkRadioButton = radioButton(PyBundle.message("runcfg.labels.use.sdk.of.module"))
            .selected(true)
            .gap(RightGap.SMALL)
            .component

          moduleComboBox = cell(ModulesComboBox())
            .align(AlignX.FILL)
            .enabledIf(useModuleSdkRadioButton.selected)
            .component
        }.layout(RowLayout.PARENT_GRID)
        row("") {
          useSpecifiedSdkRadioButton = radioButton(PyBundle.message("runcfg.labels.interpreter"))
            .gap(RightGap.SMALL)
            .component
          interpreterComboBox = comboBox(listOf<Sdk>(),
                                         PySdkListCellRenderer("<" + PyBundle.message("python.sdk.rendering.project.default") + ">"))
            .enabledIf(useSpecifiedSdkRadioButton.selected)
            .align(AlignX.FILL)
            .component
        }.layout(RowLayout.PARENT_GRID)
          .bottomGap(BottomGap.SMALL)
      }

      row(PyBundle.message("runcfg.labels.interpreter_options")) {
        interpreterOptionsTextField = cell(RawCommandLineEditor())
          .align(AlignX.FILL)
          .component
      }
      row(PyBundle.message("runcfg.labels.working_directory")) {
        workingDirectoryTextField = cell(TextFieldWithBrowseButton())
          .align(AlignX.FILL)
          .component
      }
      pathMappingsRow = row(PyBundle.message("runcfg.labels.path.mappings")) {
        pathMappingsComponent = cell(PathMappingsComponent())
          .applyToComponent { remove(label) }
          .align(AlignX.FILL)
          .component
      }
      row {
        addContentRootsCheckbox = checkBox(PyBundle.message("runcfg.labels.add.content.roots.to.pythonpath"))
          .selected(true)
          .component
      }
      row {
        addSourceRootsCheckbox = checkBox(PyBundle.message("runcfg.labels.add.source.roots.to.pythonpath"))
          .selected(true)
          .component
      }
    }.apply {
      expanded = PropertiesComponent.getInstance().getBoolean(AbstractPyCommonOptionsForm.EXPAND_PROPERTY_KEY, true)
      addExpandedListener {
        PropertiesComponent.getInstance().setValue(AbstractPyCommonOptionsForm.EXPAND_PROPERTY_KEY, it.toString(), "true")
      }
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.run.AbstractPyCommonOptionsForm
import com.jetbrains.python.run.PyCommonOptionsFormData
import javax.swing.JCheckBox
import javax.swing.JComboBox

internal class PyIdeCommonOptionsPanel(data: PyCommonOptionsFormData, showModules: Boolean, pythonSdks: List<Sdk>) {

  @JvmField
  val moduleCombo = ModulesComboBox()

  @JvmField
  val envsComponent = EnvironmentVariablesComponent()
    .apply { remove(label) }
  lateinit var interpreterComboBox: JComboBox<Sdk>

  @JvmField
  val interpreterOptionsTextField = RawCommandLineEditor()

  @JvmField
  val workingDirectoryTextField = TextFieldWithBrowseButton()

  @JvmField
  val pathMappingsComponent = PathMappingsComponent()
    .apply { remove(label) }
  lateinit var pathMappingsRow: Row
  lateinit var addContentRootsCheckbox: JCheckBox
  lateinit var addSourceRootsCheckbox: JCheckBox
  lateinit var panel: DialogPanel

  init {
    panel = panel {
      collapsibleGroup(PyBundle.message("python.sdk.common.options.environment")) {
        if (showModules) {
          row(PyBundle.message("runcfg.labels.project")) {
            cell(moduleCombo)
              .align(AlignX.FILL)
          }
        }

        row {
          cell(envsComponent)
            .label(PyBundle.message("runcfg.labels.environment_variables"), LabelPosition.TOP)
            .align(AlignX.FILL)
        }

        row(PyBundle.message("runcfg.labels.python.interpreter")) {
          interpreterComboBox = comboBox(pythonSdks)
            .columns(COLUMNS_LARGE)
            .align(AlignX.FILL)
            .component
        }

        row(PyBundle.message("runcfg.unittest.dlg.interpreter_options_title")) {
          cell(interpreterOptionsTextField)
            .align(AlignX.FILL)
        }

        row(PyBundle.message("runcfg.labels.working_directory")) {
          cell(workingDirectoryTextField)
            .align(AlignX.FILL)
            .component
        }

        pathMappingsRow = row(PyBundle.message("runcfg.labels.path.mappings")) {
          cell(pathMappingsComponent)
            .align(AlignX.FILL)
        }

        if (data.showConfigureInterpretersLink()) {
          row {
            link(PyBundle.message("configuring.interpreters.link")) {
              val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(panel))
              settings?.select(settings.find(PyActiveSdkModuleConfigurable::class.java.name))
            }
          }
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
}

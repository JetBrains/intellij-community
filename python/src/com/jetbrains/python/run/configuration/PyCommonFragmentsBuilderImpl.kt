// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.EnvFileComponent.Companion.createEnvFilesFragment
import com.jetbrains.python.run.PyCommonFragmentsBuilder
import com.jetbrains.python.run.configuration.AbstractPythonConfigurationFragmentedEditor.Companion.MIN_FRAGMENT_WIDTH
import java.awt.event.ItemEvent
import kotlin.io.path.Path

/**
 * The environment fragments of a Python run configuration, in each IDE: the module, when the project has more than one,
 * and the interpreter, see [PySdkComboBox]. The "Current Interpreter" row stands for the interpreter of that module.
 */
internal class PyCommonFragmentsBuilderImpl : PyCommonFragmentsBuilder() {

  override fun <T : AbstractPythonRunConfiguration<*>> createEnvironmentFragments(fragments: MutableList<SettingsEditorFragment<T, *>>,
                                                                                  config: T) {
    val modules = ModuleManager.getInstance(config.project).modules
    var modulesComboBox: ModulesComboBox? = null
    if (modules.size > 1) {
      modulesComboBox = ModulesComboBox()
      val modulesFragment = SettingsEditorFragment(
        "py.ide.project",
        PyBundle.message("python.run.configuration.fragments.project"),
        null,
        modulesComboBox, SettingsEditorFragmentType.COMMAND_LINE,
        { s: T, modulesCombo: ModulesComboBox ->
          val validModules: List<Module> = config.validModules
          modulesCombo.setModules(validModules)
          modulesCombo.selectedModule = config.module
        },
        { s: T, modulesCombo: ModulesComboBox ->
          modulesCombo.selectedModule?.let { selected ->
            s.module = selected
          }
        },
        { true }
      )
      modulesFragment.isRemovable = false
      modulesFragment.setHint(PyBundle.message("python.run.configuration.fragments.project.hint"))
      fragments.add(modulesFragment)
    }

    val sdkComboBox = PySdkComboBox(config.project, true) { if (modules.size > 1) modulesComboBox?.selectedModule else modules.firstOrNull() }
    CommandLinePanel.setMinimumWidth(sdkComboBox, MIN_FRAGMENT_WIDTH)
    val interpreterFragment: SettingsEditorFragment<T, PySdkComboBox> = SettingsEditorFragment<T, PySdkComboBox>(
      "py.ide.interpreter",
      PyBundle.message("python.run.configuration.fragments.interpreter.field"),
      null,
      sdkComboBox, SettingsEditorFragmentType.COMMAND_LINE,
      { s: T, c: PySdkComboBox -> c.reset(s) },
      { s: T, c: PySdkComboBox -> c.apply(s) },
      { true })
    interpreterFragment.isRemovable = false
    interpreterFragment.setHint(PyBundle.message("python.run.configuration.fragments.interpreter.field"))
    fragments.add(interpreterFragment)

    modulesComboBox?.addItemListener { e ->
      if (e?.stateChange == ItemEvent.SELECTED) {
        if (e.item is Module) sdkComboBox.moduleChanged()
      }
    }

    fragments.add(createWorkingDirectoryFragment(config.project))
    fragments.add(createEnvParameters())
    fragments.add(createEnvFilesFragment(config.project) {
      VirtualFileManager.getInstance().findFileByNioPath(Path(config.getWorkingDirectorySafe()))
    })
    fragments.add(PyPathMappingsEditorFragment(sdkComboBox))
  }
}
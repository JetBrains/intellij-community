// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ui.*
import com.intellij.openapi.components.service
import com.intellij.ui.RawCommandLineEditor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PyCommonFragmentsBuilder
import com.jetbrains.python.run.PythonRunConfigurationExtensionsManager

abstract class AbstractPythonConfigurationFragmentedEditor<T : AbstractPythonRunConfiguration<*>>(val runConfiguration: T) :
  RunConfigurationFragmentedEditor<T>(runConfiguration, PythonRunConfigurationExtensionsManager.instance) {

  override fun createRunFragments(): MutableList<SettingsEditorFragment<T, *>> {
    val fragments: MutableList<SettingsEditorFragment<T, *>> = ArrayList()
    val beforeRunComponent = BeforeRunComponent(this)
    fragments.add(BeforeRunFragment.createBeforeRun(beforeRunComponent, null))
    fragments.addAll(BeforeRunFragment.createGroup())
    fragments.add(CommonParameterFragments.createRunHeader())
    fragments.add(CommonTags.parallelRun())

    createEnvironmentFragments(fragments, runConfiguration)
    addInterpreterOptions(fragments)
    addContentSourceRoots(fragments)

    customizeFragments(fragments)
    fragments.add(PyEditorExtensionFragment())

    fragments.add(LogsGroupFragment())
    return fragments
  }

  private fun <T : AbstractPythonRunConfiguration<*>> createEnvironmentFragments(fragments: MutableList<SettingsEditorFragment<T, *>>,
                                                                                 runConfiguration: T) {
    service<PyCommonFragmentsBuilder>().createEnvironmentFragments(fragments, runConfiguration)
  }

  abstract fun customizeFragments(fragments: MutableList<SettingsEditorFragment<T, *>>)

  private fun <T : AbstractPythonRunConfiguration<*>> addInterpreterOptions(fragments: MutableList<SettingsEditorFragment<T, *>>) {
    val interpreterOptionsField = RawCommandLineEditor()
    val interpreterOptionsFragment: SettingsEditorFragment<T, RawCommandLineEditor> = SettingsEditorFragment<T, RawCommandLineEditor>(
      "py.interpreter.options",
      PyBundle.message("python.run.configuration.fragments.interpreter.options"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      interpreterOptionsField, SettingsEditorFragmentType.COMMAND_LINE,
      { config: T, field: RawCommandLineEditor -> field.text = config.interpreterOptions },
      { config: T, field: RawCommandLineEditor -> config.interpreterOptions = field.text.trim() },
      { config: T -> !config.interpreterOptions.trim().isEmpty() })
    interpreterOptionsFragment.setHint(PyBundle.message("python.run.configuration.fragments.interpreter.options.hint"))
    interpreterOptionsFragment.actionHint = PyBundle.message("python.run.configuration.fragments.interpreter.options.hint")
    fragments.add(interpreterOptionsFragment)
  }

  private fun <T : AbstractPythonRunConfiguration<*>> addContentSourceRoots(fragments: MutableList<SettingsEditorFragment<T, *>>) {
    val addContentRoots = SettingsEditorFragment.createTag<T>(
      "py.add.content.roots",
      PyBundle.message("python.run.configuration.fragments.content.roots"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      { it.shouldAddContentRoots() },
      { config, value -> config.setAddContentRoots(value) })

    addContentRoots.actionHint = PyBundle.message("python.run.configuration.fragments.content.roots.hint")
    fragments.add(addContentRoots)

    val addSourceRoots = SettingsEditorFragment.createTag<T>(
      "py.add.source.roots",
      PyBundle.message("python.run.configuration.fragments.source.roots"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      { it.shouldAddSourceRoots() },
      { config, value -> config.setAddSourceRoots(value) })

    addSourceRoots.actionHint = PyBundle.message("python.run.configuration.fragments.source.roots.hint")
    fragments.add(addSourceRoots)
  }

  companion object {
    const val MIN_FRAGMENT_WIDTH = 500
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ui.*
import com.intellij.openapi.components.service
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.TextComponentEmptyText
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PyCommonFragmentsBuilder
import com.jetbrains.python.run.PythonRunConfigurationExtensionsManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

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
      interpreterOptionsField,
      SettingsEditorFragmentType.COMMAND_LINE,
      { config: T, field: RawCommandLineEditor -> field.text = config.interpreterOptions },
      { config: T, field: RawCommandLineEditor -> config.interpreterOptions = field.text.trim() },
      { config: T -> !config.interpreterOptions.trim().isEmpty() })
    interpreterOptionsField.editorField.emptyText.setText(PyBundle.message("python.run.configuration.fragments.interpreter.options.placeholder"))
    TextComponentEmptyText.setupPlaceholderVisibility(interpreterOptionsField.editorField)
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

  protected fun <U : AbstractPythonRunConfiguration<*>> addSingleSelectionListeners(editors: MutableList<SettingsEditorFragment<U, *>>) {
    for ((i, editor) in editors.withIndex()) {
      editor.component().addComponentListener(object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent?) {
          for ((j, otherEditor) in editors.withIndex()) {
            if (i != j) {
              otherEditor.isSelected = false
            }
          }
        }
      })
    }
  }

  fun addToFragmentsBeforeEditors(fragments: MutableList<SettingsEditorFragment<T, *>>, newFragment: SettingsEditorFragment<T, *>) {
    // Q: not sure whether it makes sense to make it more generic, not only for EDITOR type
    val index = fragments.indexOfFirst { it.isEditor }
    if (index == -1) {
      fragments.add(newFragment)
    } else {
      fragments.add(index, newFragment)
    }
  }

  companion object {
    const val MIN_FRAGMENT_WIDTH = 500
  }
}

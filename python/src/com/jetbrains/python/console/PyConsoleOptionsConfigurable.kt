// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

private const val CONSOLE_SETTINGS_HELP_REFERENCE: String = "reference.project.settings.console"


@ApiStatus.Internal
class PyConsoleOptionsConfigurable(private val myProject: Project) : SearchableConfigurable.Parent.Abstract() {

  enum class CodeCompletionOption {
    RUNTIME, STATIC;

    override fun toString(): String = when (this) {
      RUNTIME -> PyBundle.message("form.console.options.runtime.code.completion")
      STATIC -> PyBundle.message("form.console.options.static.code.completion")
    }
  }

  private var myPanel: DialogPanel? = null

  /**
   * The former "Python Console" child page. Its settings now live on this page.
   *
   * Recreated for every [createComponent] call: the panel owns a one-shot [com.intellij.openapi.Disposable]
   * for its SDK-table subscription, so a reused instance would subscribe to a disposed parent.
   */
  private var myPythonConsolePanel: PyConsoleSpecificOptionsPanel? = null

  override fun getId(): String = "pyconsole"

  override fun getDisplayName(): @Nls String = PyBundle.message("configurable.PyConsoleOptionsConfigurable.display.name")

  override fun getHelpTopic(): String = CONSOLE_SETTINGS_HELP_REFERENCE

  override fun hasOwnContent(): Boolean = true

  override fun buildConfigurables(): Array<Configurable> {
    val result = mutableListOf<Configurable>()
    // The Python console page is merged into this one. Only the framework consoles stay as child pages.
    for (provider in PyConsoleOptionsProvider.EP_NAME.extensionList) {
      if (provider.isApplicableTo(myProject)) {
        result.add(createChildConfigurable(provider.name,
                                           PyConsoleSpecificOptionsPanel(myProject),
                                           provider.getSettings(myProject),
                                           provider.helpTopic))
      }
    }
    return result.toTypedArray()
  }

  override fun createComponent(): JComponent {
    if (myPanel == null) myPanel = createPanel()
    return myPanel!!
  }

  // myPanel guards every call below. The platform may ask about state before createComponent, and
  // PyConsoleSpecificOptionsPanel keeps its settings in lateinit fields until its panel is built.
  override fun isModified(): Boolean =
    myPanel?.let { it.isModified() || myPythonConsolePanel?.isModified == true } == true

  override fun apply() {
    myPanel?.apply()
    myPythonConsolePanel?.apply()
  }

  override fun reset() {
    myPanel?.reset()
    myPythonConsolePanel?.reset()
  }

  override fun disposeUIResources() {
    myPanel = null
    myPythonConsolePanel?.disposeUIResources()
    myPythonConsolePanel = null
    super.disposeUIResources()
  }

  private fun createPanel(): DialogPanel {
    val consoleOptions = PyConsoleOptions.getInstance(myProject)
    val pythonConsolePanel = PyConsoleSpecificOptionsPanel(myProject)
    myPythonConsolePanel = pythonConsolePanel
    val pythonConsole = pythonConsolePanel.createPanel(consoleOptions.pythonConsoleSettings)
    return panel {
      row {
        checkBox(PyBundle.message("form.console.options.use.ipython.if.available"))
          .bindSelected(consoleOptions::isIpythonEnabled)
      }
      row {
        checkBox(PyBundle.message("form.console.options.show.console.variables.by.default"))
          .bindSelected(consoleOptions::isShowVariableByDefault)
      }
      row {
        checkBox(PyBundle.message("form.console.options.use.existing.console.for.run.with.python.console"))
          .bindSelected(consoleOptions::isUseExistingConsole)
      }
      row {
        checkBox(PyBundle.message("form.console.options.use.command.queue"))
          .bindSelected(consoleOptions::isCommandQueueEnabled)
      }
      row(PyBundle.message("form.console.options.code.completion")) {
        comboBox(CodeCompletionOption.entries)
          .bindItem({ consoleOptions.codeCompletionOption }, { if (it != null) consoleOptions.codeCompletionOption = it })
      }
      row {
        cell(pythonConsole).align(Align.FILL)
      }.resizableRow()
    }
  }

  private fun createChildConfigurable(
    @NlsContexts.ConfigurableName name: String,
    panel: PyConsoleSpecificOptionsPanel,
    settings: PyConsoleOptions.PyConsoleSettings,
    helpReference: String,
  ): SearchableConfigurable {
    return object : SearchableConfigurable, Configurable.NoScroll {
      override fun getId(): String = "PyConsoleConfigurable.$name"
      override fun getDisplayName(): @NlsContexts.ConfigurableName String = name
      override fun getHelpTopic(): String = helpReference
      override fun createComponent(): JComponent = panel.createPanel(settings)
      override fun isModified(): Boolean = panel.isModified
      override fun apply() = panel.apply()
      override fun reset() = panel.reset()
      override fun disposeUIResources() = panel.disposeUIResources()
    }
  }
}
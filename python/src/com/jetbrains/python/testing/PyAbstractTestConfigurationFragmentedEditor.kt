// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.TextComponentEmptyText
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.configuration.AbstractPyRunConfigTargetChooserFragment
import com.jetbrains.python.run.configuration.AbstractPythonConfigurationFragmentedEditor
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectTestConfiguration
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout


class PyAbstractTestConfigurationFragmentedEditor(runConfiguration: PyAbstractTestConfiguration) :
  AbstractPythonConfigurationFragmentedEditor<PyAbstractTestConfiguration>(runConfiguration) {

  @Nls private val groupName = when (runConfiguration) {
    is PyTestConfiguration -> PyBundle.message("runcfg.pytest.parameters.group.name")
    is PyAutoDetectTestConfiguration -> PyBundle.message("runcfg.autodetect.parameters.group.name")
    else -> PyBundle.message("runcfg.test.unknown.group")
  }

  init {
    // Inherited from 'PyAbstractTestConfiguration' to maintain compatibility with older UI.
    require(
      runConfiguration is PyTestConfiguration
      || runConfiguration is PyAutoDetectTestConfiguration)
    {
      "PyAbstractTestConfigurationFragmentedEditor is supported only" +
      "for PyTestConfiguration and PyAutoDetectTestConfiguration"
    }
  }

  override fun customizeFragments(fragments: MutableList<SettingsEditorFragment<PyAbstractTestConfiguration, *>>) {
    val targetSelectorFragment = PyTestTargetChooserFragment()
    fragments.add(targetSelectorFragment)

    val additionalOptionsFragment = createAdditionalArgumentsFragment()
    fragments.add(additionalOptionsFragment)

    if (runConfiguration is PyTestConfiguration) {
      val keywordsFragment = createKeywordsFragment()
      fragments.add(keywordsFragment)
      addParametersFragment(fragments)
    }
  }

  private fun createAdditionalArgumentsFragment(): SettingsEditorFragment<PyAbstractTestConfiguration, *> {
    val additionalOptionsEditor = RawCommandLineEditor()
    CommandLinePanel.setMinimumWidth(additionalOptionsEditor, MIN_FRAGMENT_WIDTH)
    additionalOptionsEditor.editorField.emptyText.setText(PyBundle.message("runcfg.pytest.config.additional.arguments"))
    TextComponentEmptyText.setupPlaceholderVisibility(additionalOptionsEditor.editorField)

    val additionalOptionsFragment = SettingsEditorFragment<PyAbstractTestConfiguration, RawCommandLineEditor>(
      "pytest.additional.arguments",
      PyBundle.message("runcfg.pytest.config.additional.arguments"),
      groupName,
      additionalOptionsEditor,
      SettingsEditorFragmentType.COMMAND_LINE,
      { config, field -> field.text = config.additionalArguments },
      { config, field -> config.additionalArguments = field.text.trim() },
      { true }
    )
    additionalOptionsFragment.setHint(PyBundle.message("runcfg.pytest.config.additional.arguments.hint"))
    additionalOptionsFragment.actionHint = PyBundle.message("runcfg.pytest.config.additional.arguments.action.hint")
    return additionalOptionsFragment
  }

  private fun createKeywordsFragment(): SettingsEditorFragment<PyAbstractTestConfiguration, *> {
    val keywordsEditor = RawCommandLineEditor()
    CommandLinePanel.setMinimumWidth(keywordsEditor, MIN_FRAGMENT_WIDTH)
    keywordsEditor.editorField.emptyText.setText(PyBundle.message("runcfg.pytest.config.keywords"))
    TextComponentEmptyText.setupPlaceholderVisibility(keywordsEditor.editorField)

    val keywordsFragment = SettingsEditorFragment<PyAbstractTestConfiguration, RawCommandLineEditor>(
      "pytest.keywords",
      PyBundle.message("runcfg.pytest.config.keywords"),
      groupName,
      keywordsEditor,
      SettingsEditorFragmentType.COMMAND_LINE,
      { config, field -> field.text = (config as PyTestConfiguration).keywords },
      { config, field -> (config as PyTestConfiguration).keywords = field.text.trim() },
      { true }
    )
    keywordsFragment.setHint(PyBundle.message("runcfg.pytest.config.keywords.hint"))
    keywordsFragment.actionHint = PyBundle.message("runcfg.pytest.config.keywords.action.hint")
    return keywordsFragment
  }

  private fun addParametersFragment(fragments: MutableList<SettingsEditorFragment<PyAbstractTestConfiguration, *>>) {
    val browserUrl = RawCommandLineEditor()
    val labeledComponent = LabeledComponent.create(browserUrl, PyBundle.message("runcfg.pytest.config.parameters.label"))
    labeledComponent.labelLocation = BorderLayout.WEST

    val browserRunFragment = SettingsEditorFragment(
      "pytest.parameters",
      PyBundle.message("runcfg.pytest.config.parameters"),
      groupName,
      labeledComponent,
      SettingsEditorFragmentType.EDITOR,
      { config: PyAbstractTestConfiguration, component -> component.component.text = (config as PyTestConfiguration).parameters },
      { config: PyAbstractTestConfiguration, component -> (config as PyTestConfiguration).parameters = component.component.text },
      { config: PyAbstractTestConfiguration -> (config as PyTestConfiguration).parameters.isNotEmpty() }
    )
    browserRunFragment.actionHint = PyBundle.message("runcfg.pytest.config.parameters.action.hint")
    addToFragmentsBeforeEditors(fragments, browserRunFragment)
  }
}


class PyTestTargetChooserFragment: AbstractPyRunConfigTargetChooserFragment<PyAbstractTestConfiguration>() {
  override fun applyEditorTo(s: PyAbstractTestConfiguration) {
    val mode = currentMode
    if (mode == null) return
    val targetType = modeToPyRunTargetVariant[mode]
    if (targetType == null) return
    val text = (fields[mode] as? TextAccessor)?.text?.trim() ?: ""
    s.target.target = text
    s.target.targetType = targetType
  }

  override fun resetEditorFrom(config: PyAbstractTestConfiguration) {
    if (currentMode == null) {
      initComponents(config)
    }

    val type = config.target.targetType
    val typeNum = runTargetVariantToMode[type]
    if (typeNum == null) return

    if (currentMode != typeNum) {
      currentMode = typeNum
      moduleModeChooser.selectedItem = typeNames[typeNum]
      showField(typeNum)
      (fields[typeNum] as TextAccessor).text = config.target.target
    }
  }
}

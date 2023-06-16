// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Predicates
import com.intellij.ui.components.fields.ExtendableTextField
import java.awt.BorderLayout
import javax.swing.JComponent


abstract class PyCommonFragmentsBuilder {
  abstract fun <T : AbstractPythonRunConfiguration<*>> createEnvironmentFragments(fragments: MutableList<SettingsEditorFragment<T, *>>,
                                                                                  config: T)

  fun <T : AbstractPythonRunConfiguration<*>> createWorkingDirectoryFragment(project: Project):
    SettingsEditorFragment<T, LabeledComponent<TextFieldWithBrowseButton>> {
    val textField = ExtendableTextField(10)
    val workingDirectoryField = TextFieldWithBrowseButton(textField)
    workingDirectoryField.addBrowseFolderListener(
      ExecutionBundle.message("select.working.directory.message"),
      null,
      project,
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    )
    val field = LabeledComponent.create(
      workingDirectoryField,
      ExecutionBundle.message("run.configuration.working.directory.label"),
      BorderLayout.WEST
    )
    val workingDirectorySettings = SettingsEditorFragment(
      "workingDirectory",
      ExecutionBundle.message("run.configuration.working.directory.name"), null, field,
      { config: T, component: LabeledComponent<TextFieldWithBrowseButton> -> component.component.text = config.workingDirectory },
      { config: T, component: LabeledComponent<TextFieldWithBrowseButton> -> config.workingDirectory = component.component.text },
      Predicates.alwaysTrue()
    )
    workingDirectorySettings.isRemovable = false
    return workingDirectorySettings
  }

  fun <T : AbstractPythonRunConfiguration<*>> createEnvParameters(): SettingsEditorFragment<T, *> {
    val env = EnvironmentVariablesComponent()
    env.labelLocation = BorderLayout.WEST
    CommonParameterFragments.setMonospaced(env.component.textField)
    val fragment = SettingsEditorFragment<T, JComponent>(
      "environmentVariables",
      ExecutionBundle.message("environment.variables.fragment.name"),
      ExecutionBundle.message("group.operating.system"), env,
      { config: T, c: JComponent? ->
        env.envs = config.envs
        env.isPassParentEnvs = config.isPassParentEnvs
      },
      { config: T, _: JComponent? ->
        if (!env.isVisible) {
          config.envs = emptyMap()
          config.isPassParentEnvs = true
        }
        else {
          config.envs = env.envs
          config.isPassParentEnvs = env.isPassParentEnvs
        }
      },
      { true })
    fragment.isCanBeHidden = true
    fragment.setHint(ExecutionBundle.message("environment.variables.fragment.hint"))
    fragment.actionHint = ExecutionBundle.message("set.custom.environment.variables.for.the.process")
    return fragment
  }

}
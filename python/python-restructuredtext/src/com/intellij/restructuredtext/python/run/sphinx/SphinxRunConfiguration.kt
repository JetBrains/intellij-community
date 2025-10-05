// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python.run.sphinx

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.restructuredtext.python.PythonRestBundle.message
import com.intellij.restructuredtext.python.run.RestConfigurationEditor
import com.intellij.restructuredtext.python.run.RestRunConfiguration
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot

/**
 * User : catherine
 */
class SphinxRunConfiguration(
  project: Project?,
  factory: ConfigurationFactory?,
) : RestRunConfiguration(project, factory) {
  override fun createConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
    val model = SphinxTasksModel()
    val sdk = sdk
    if (!model.contains("pdf") && sdk != null) {
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val isInstalled = packageManager.hasInstalledPackageSnapshot("rst2pdf")
      if (isInstalled) {
        model.add(13, "pdf")
      }
    }

    val editor = RestConfigurationEditor(project, this, model)
    editor.setConfigurationName("Sphinx task")
    editor.setOpenInBrowserVisible(false)
    editor.setInputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
    editor.setOutputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
    return editor
  }

  @Throws(ExecutionException::class)
  override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState {
    return SphinxCommandLineState(this, env)
  }

  @Throws(RuntimeConfigurationException::class)
  override fun checkConfiguration() {
    super.checkConfiguration()
    if (inputFile.isNullOrBlank()) throw RuntimeConfigurationError(message("python.rest.specify.input.directory.name"))
    if (outputFile.isNullOrBlank()) throw RuntimeConfigurationError(message("python.rest.specify.output.directory.name"))
  }

  override fun suggestedName(): String {
    return message("python.rest.sphinx.run.cfg.default.name", name)
  }
}

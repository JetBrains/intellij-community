// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.autoDetectTests

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.BeanBinding
import com.jetbrains.python.testing.PyAbstractTestConfiguration
import com.jetbrains.python.testing.PyAbstractTestSettingsEditor
import com.jetbrains.python.testing.PyTestSharedForm
import com.jetbrains.python.reflection.getProperties

class PyAutoDetectTestConfiguration(project: Project, factory: PyAutoDetectionConfigurationFactory)
  : PyAbstractTestConfiguration(project, factory) {

  // "Autodetect" name is useless
  override val useFrameworkNameInConfiguration: Boolean = false

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    val runProfile = environment.runProfile
    val module = (runProfile as? PyAbstractTestConfiguration)?.module
    val conf = detectedConfiguration(module) ?: return null

    copyTo(getProperties(conf))
    for (accessor in BeanBinding.getAccessors(PyAbstractTestConfiguration::class.java)) {
      accessor.set(conf, accessor.read(this))
    }
    conf.setAddContentRoots(shouldAddContentRoots())
    conf.setAddSourceRoots(shouldAddSourceRoots())
    conf.mappingSettings = mappingSettings
    conf.beforeRunTasks = beforeRunTasks
    return conf.getState(executor, environment)
  }

  fun detectedConfiguration(module: Module?): PyAbstractTestConfiguration? {
    return PyAutoDetectionConfigurationFactory.factoriesExcludingThis
      .asSequence().map {
        it.createTemplateConfiguration(project).apply {
          this.module = module
        }
      }.filter {
        it.isFrameworkInstalled()
      }.firstOrNull()
  }

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> {
    return object : PyAbstractTestSettingsEditor(PyTestSharedForm.create(this)) {}
  }

}

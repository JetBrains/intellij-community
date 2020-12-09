// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class SpaceRunConfiguration(project: Project, factory: ConfigurationFactory)
  : LocatableConfigurationBase<SpaceRunTaskConfigurationOptions>(project, factory, "Run") {

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
    return SpaceRunTaskConfigurationEditor()
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    return SpaceRunTaskState(options, environment)
  }

  public override fun getOptions(): SpaceRunTaskConfigurationOptions {
    return super.getOptions() as SpaceRunTaskConfigurationOptions
  }
}

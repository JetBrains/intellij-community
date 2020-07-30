package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class CircletRunConfiguration(project: Project, factory: ConfigurationFactory)
  : LocatableConfigurationBase<CircletRunTaskConfigurationOptions>(project, factory, "Run") {

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
    return CircletRunTaskConfigurationEditor()
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    return CircletRunTaskState(options, environment)
  }

  public override fun getOptions(): CircletRunTaskConfigurationOptions {
    return super.getOptions() as CircletRunTaskConfigurationOptions
  }
}

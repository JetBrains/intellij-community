package com.intellij.space.plugins.pipelines.services.run

import com.intellij.space.plugins.pipelines.services.execution.SpaceTaskRunner
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service

class SpaceRunTaskState(private val settings: SpaceRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(
  environment) {

  override fun startProcess(): ProcessHandler {
    val project = environment.project
    val runner = project.service<SpaceTaskRunner>()
    val taskName = settings.taskName
    if (taskName == null) {
      throw ExecutionException("TaskName is null")
    }
    else {
      return runner.run(taskName)
    }
  }

}

package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project

object SpaceRunConfigurationUtils {
  fun run(taskName: String, project: Project) {
    val runManager = RunManager.getInstance(project)
    val settings = runManager.createConfiguration("Run task $taskName", SpaceRunConfigurationType::class.java)
    val configuration = settings.configuration as SpaceRunConfiguration
    configuration.options.taskName = taskName
    runManager.addConfiguration(settings)
    runManager.selectedConfiguration = settings
    ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
  }
}

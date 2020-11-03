// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.space.plugins.pipelines.services.execution.SpaceTaskRunner

class SpaceRunTaskState(private val settings: SpaceRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(
  environment) {

  override fun startProcess(): ProcessHandler {
    val project = environment.project
    val runner = project.service<SpaceTaskRunner>()
    val taskName = settings.taskName
    if (taskName == null) {
      throw ExecutionException("TaskName is null") // NON-NLS
    }
    else {
      return runner.run(taskName)
    }
  }

}

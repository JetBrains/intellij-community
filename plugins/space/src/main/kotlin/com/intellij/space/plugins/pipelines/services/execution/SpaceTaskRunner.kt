// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import libraries.io.random.Random
import libraries.klogging.KLogging
import java.io.OutputStream

@Service
class SpaceTaskRunner(val project: Project) {
  companion object : KLogging()

  private val ideaLocalRunnerLabel = "SpaceTaskRunner_${Random.nextString(10)}"

  fun run(taskName: String): ProcessHandler {
    // todo: implement using local run from the SDK.
    return TaskProcessHandler(taskName)
  }

  private val newLine: String = System.getProperty("line.separator", "\n")
}

class TaskProcessHandler(private val taskName: String) : ProcessHandler() {

  companion object : KLogging()

  override fun getProcessInput(): OutputStream? {
    return null
  }

  override fun detachIsDefault(): Boolean {
    return false
  }

  override fun detachProcessImpl() {
    logger.info("detachProcessImpl for task $taskName")
  }

  override fun destroyProcessImpl() {
    logger.info("destroyProcessImpl for task $taskName")
    notifyProcessTerminated(0)
  }

  fun dispose() {
    notifyProcessTerminated(0)
  }


}

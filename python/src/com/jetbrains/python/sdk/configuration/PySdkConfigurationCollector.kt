// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object PySdkConfigurationCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.sdk.configuration", 2)

  override fun getGroup(): EventLogGroup = GROUP

  internal enum class VirtualEnvResult { DEPS_NOT_FOUND, CREATED }
  internal enum class CondaEnvResult { CREATION_FAILURE, CREATED }
  internal enum class PipEnvResult { CREATION_FAILURE, CREATED }


  internal fun logVirtualEnv(project: Project, result: VirtualEnvResult): Unit = venvEvent.log(project, result)

  internal fun logCondaEnv(project: Project, result: CondaEnvResult): Unit = condaEnvEvent.log(project, result)

  internal fun logPipEnv(project: Project, result: PipEnvResult): Unit = pipenvEvent.log(project, result)


  private val venvEvent = GROUP.registerEvent("venv.created", EventFields.Enum("env_result", VirtualEnvResult::class.java))

  private val condaEnvEvent = GROUP.registerEvent("condaEnv.created", EventFields.Enum("env_result", CondaEnvResult::class.java))

  private val pipenvEvent = GROUP.registerEvent("pipenv.created", EventFields.Enum("env_result", PipEnvResult::class.java))
}
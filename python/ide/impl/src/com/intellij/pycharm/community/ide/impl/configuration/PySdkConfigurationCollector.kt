// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object PySdkConfigurationCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.sdk.configuration", 2)

  override fun getGroup(): EventLogGroup = GROUP

  internal enum class Source { CONFIGURATOR, INSPECTION }
  internal enum class InputData { NOT_FILLED, SPECIFIED }
  internal enum class VirtualEnvResult { DEPS_NOT_FOUND, INSTALLATION_FAILURE, }
  internal enum class CondaEnvResult { CREATION_FAILURE, CREATED }
  internal enum class PipEnvResult { CREATION_FAILURE, CREATED }
  private enum class DialogResult { OK, CANCELLED, SKIPPED }


  internal fun logVirtualEnvDialog(project: Project, permitted: Boolean, source: Source, baseSdk: InputData) {
    venvDialogEvent.log(project, permitted.asDialogResult, source, baseSdk)
  }

  internal fun logVirtualEnv(project: Project, result: VirtualEnvResult): Unit = venvEvent.log(project, result)

  internal fun logCondaEnvDialog(project: Project, permitted: Boolean, source: Source, condaPath: InputData) {
    condaEnvDialogEvent.log(project, permitted.asDialogResult, source, condaPath)
  }

  internal fun logCondaEnvDialogSkipped(project: Project, source: Source, condaPath: InputData) {
    condaEnvDialogEvent.log(project, DialogResult.SKIPPED, source, condaPath)
  }

  internal fun logCondaEnv(project: Project, result: CondaEnvResult): Unit = condaEnvEvent.log(project, result)

  internal fun logPipEnvDialog(project: Project, permitted: Boolean, source: Source, pipenvPath: InputData) {
    pipenvDialogEvent.log(project, permitted.asDialogResult, source, pipenvPath)
  }

  internal fun logPipEnv(project: Project, result: PipEnvResult): Unit = pipenvEvent.log(project, result)


  private val Boolean.asDialogResult: DialogResult
    get() = if (this) DialogResult.OK else DialogResult.CANCELLED

  private val venvDialogEvent = GROUP.registerEvent(
    "venv.dialog.closed",
    EventFields.Enum("dialog_result", DialogResult::class.java),
    EventFields.Enum("source", Source::class.java),
    EventFields.Enum("baseSdk", InputData::class.java)
  )

  private val venvEvent = GROUP.registerEvent("venv.created", EventFields.Enum("env_result", VirtualEnvResult::class.java))

  private val condaEnvDialogEvent = GROUP.registerEvent(
    "condaEnv.dialog.closed",
    EventFields.Enum("dialog_result", DialogResult::class.java),
    EventFields.Enum("source", Source::class.java),
    EventFields.Enum("conda_path", InputData::class.java)
  )

  private val condaEnvEvent = GROUP.registerEvent("condaEnv.created", EventFields.Enum("env_result", CondaEnvResult::class.java))

  private val pipenvDialogEvent = GROUP.registerEvent(
    "pipenv.dialog.closed",
    EventFields.Enum("dialog_result", DialogResult::class.java),
    EventFields.Enum("source", Source::class.java),
    EventFields.Enum("pipenv_path", InputData::class.java)
  )

  private val pipenvEvent = GROUP.registerEvent("pipenv.created", EventFields.Enum("env_result", PipEnvResult::class.java))
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object PyAddNewEnvCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  internal enum class InputData { BLANK_UNCHANGED, SPECIFIED, CHANGED, UNCHANGED }
  internal enum class RequirementsTxtOrSetupPyData {
    BLANK_UNCHANGED,

    TXT_SPECIFIED,
    PY_SPECIFIED,
    OTHER_SPECIFIED,

    CHANGED_TXT_TO_OTHER,
    CHANGED_TXT_TO_PY,
    CHANGED_TXT_TO_TXT,

    CHANGED_PY_TO_OTHER,
    CHANGED_PY_TO_PY,
    CHANGED_PY_TO_TXT,

    CHANGED_OTHER_TO_OTHER,
    CHANGED_OTHER_TO_PY,
    CHANGED_OTHER_TO_TXT,

    UNCHANGED
  }

  internal fun logVirtualEnvFromFileData(project: Project,
                                         path: InputData,
                                         baseSdk: InputData,
                                         requirementsTxtOrSetupPyPath: RequirementsTxtOrSetupPyData) {
    venvFromFileDataEvent.log(project, path, baseSdk, requirementsTxtOrSetupPyPath)
  }

  internal fun logCondaEnvFromFileData(project: Project, condaPath: InputData, environmentYmlPath: InputData) {
    condaEnvFromFileDataEvent.log(project, condaPath, environmentYmlPath)
  }

  private val GROUP = EventLogGroup("python.sdk.addNewEnv", 1)

  private val venvFromFileDataEvent = GROUP.registerEvent(
    "venvFromFile.data.confirmed",
    EventFields.Enum("path", InputData::class.java),
    EventFields.Enum("baseSdk", InputData::class.java),
    EventFields.Enum("requirementsTxtOrSetupPy_path", RequirementsTxtOrSetupPyData::class.java)
  )

  private val condaEnvFromFileDataEvent = GROUP.registerEvent(
    "condaFromFile.data.confirmed",
    EventFields.Enum("conda_path", InputData::class.java),
    EventFields.Enum("environmentYml_path", InputData::class.java)
  )
}
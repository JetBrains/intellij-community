// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

enum class CommandType(val command: String) {
  PYTHON("python"),
  PIP("pip"),
  CONDA("conda")
}

internal object PyRunAnythingCollector : CounterUsagesCollector() {
  private val PY_RUN_ANY_GROUP: EventLogGroup = EventLogGroup("python.run.anything", 3)
  private val EXECUTED: EventId1<CommandType> = PY_RUN_ANY_GROUP.registerEvent("executed",
                                                                               EventFields.Enum<CommandType>("command_type"))

  fun logEvent(command: CommandType) {
    EXECUTED.log(command)
  }

  override fun getGroup(): EventLogGroup = PY_RUN_ANY_GROUP
}
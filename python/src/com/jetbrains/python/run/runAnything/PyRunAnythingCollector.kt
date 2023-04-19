// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PyRunAnythingCollector : CounterUsagesCollector() {
  companion object {
    private val PY_RUN_ANY_GROUP: EventLogGroup = EventLogGroup("python.run.anything", 1)
    private val PYTHON: EventId1<Int> = PY_RUN_ANY_GROUP.registerEvent("py.run.any.python", EventFields.Count)
    private val PIP: EventId1<Int> = PY_RUN_ANY_GROUP.registerEvent("py.run.any.pip", EventFields.Count)

    enum class CommandType(val command: String) {
      PYTHON("python"),
      PIP("pip")
    }

    fun logEvent(command: CommandType) {
      when (command) {
        CommandType.PYTHON -> PYTHON.log(1)
        CommandType.PIP -> PIP.log(1)
      }
    }
  }

  override fun getGroup(): EventLogGroup {
    return PY_RUN_ANY_GROUP
  }
}
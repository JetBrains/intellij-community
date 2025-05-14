// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.collector

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.statistics.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal

object PythonNewInterpreterAddedCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.new.interpreter.added", 4)
  private val PROJECT_GENERATED_EVENT = GROUP.registerVarargEvent("interpreted.added",
                                                                  INTERPRETER_TYPE,
                                                                  EXECUTION_TYPE,
                                                                  PYTHON_VERSION)

  fun logPythonNewInterpreterAdded(sdk: Sdk) {
    PROJECT_GENERATED_EVENT.log(
      INTERPRETER_TYPE.with(sdk.interpreterType.value),
      EXECUTION_TYPE.with(sdk.executionType.value),
      PYTHON_VERSION.with(sdk.version.toPythonVersion())
    )
  }
}

data class InterpreterStatisticsInfo(val type: InterpreterType,
                                     val target: InterpreterTarget,
                                     val globalSitePackage: Boolean,
                                     val makeAvailableToAllProjects: Boolean,
                                     val previouslyConfigured: Boolean)


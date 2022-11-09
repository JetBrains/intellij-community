// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PythonPackagesDialogStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("python.packages.dialog", 1)

    @JvmStatic
    val packagingOperationEvent = GROUP.registerEvent("packaging")
  }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PythonPackagesToolwindowStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("python.packages.toolwindow", 1)

    @JvmStatic
    val installPackageEvent = GROUP.registerEvent("installed")

    @JvmStatic
    val uninstallPackageEvent = GROUP.registerEvent("uninstalled")

    @JvmStatic
    val requestDetailsEvent = GROUP.registerEvent("details.requested")

    @JvmStatic
    val repositoriesChangedEvent = GROUP.registerEvent("repositories.changed")
  }
}
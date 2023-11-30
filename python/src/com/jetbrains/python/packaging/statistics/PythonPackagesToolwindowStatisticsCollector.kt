// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object PythonPackagesToolwindowStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  private val GROUP = EventLogGroup("python.packages.toolwindow", 1)

  val installPackageEvent = GROUP.registerEvent("installed")
  val uninstallPackageEvent = GROUP.registerEvent("uninstalled")
  val requestDetailsEvent = GROUP.registerEvent("details.requested")
  val repositoriesChangedEvent = GROUP.registerEvent("repositories.changed")
}
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PyPackagesUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  @Suppress("CompanionObjectInExtension")
  companion object {
    private val GROUP = EventLogGroup("py.packages.features", 5)

    val installAllEvent: EventId1<Int> = GROUP.registerEvent("install.all.missed.quickfix", EventFields.Count)

    @JvmField
    val installAllCanceledEvent: EventId = GROUP.registerEvent("install.all.missed.quickfix.canceled")

    @JvmField
    val installSingleEvent: EventId = GROUP.registerEvent("install.single.quickfix")

    val installPackageFromConsole: EventId = GROUP.registerEvent("install.single.from.console")
    val failInstallPackageFromConsole: EventId = GROUP.registerEvent("install.single.from.console.failed")
    val cancelInstallPackageFromConsole: EventId = GROUP.registerEvent("install.single.from.console.canceled")
  }
}

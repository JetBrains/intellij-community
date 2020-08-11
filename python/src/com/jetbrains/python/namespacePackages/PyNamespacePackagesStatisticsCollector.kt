// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.namespacePackages

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PyNamespacePackagesStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    fun logNamespacePackageCreatedByUser() {
      namespacePackageCreatedEvent.log()
    }

    fun logToggleMarkingAsNamespacePackage(mark: Boolean) {
      toggleMarkingAsNamespacePackageEvent.log(mark)
    }

    fun logApplyInNamespacePackageRootProvider() {
      namespacePackagesEditedViaProjectStructure.log()
    }

    private val GROUP = EventLogGroup("python.namespace.packages.events", 1)

    private val namespacePackageCreatedEvent = GROUP.registerEvent("namespace.package.created")

    private val toggleMarkingAsNamespacePackageEvent = GROUP.registerEvent("namespace.package.mark.or.unmark", EventFields.Boolean("is_mark"))

    private val namespacePackagesEditedViaProjectStructure = GROUP.registerEvent("namespace.package.apply.in.root.provider")
  }
}
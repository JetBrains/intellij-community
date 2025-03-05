// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

/**
 * Logs misc project creation and renaming to FUS
 */
internal object MiscProjectUsageCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("pycharm.misc.project", 3)

  private val MISC_PROJECT_CREATED: EventId = GROUP.registerEvent("misc.project.created", "Misc project created (user clicked on the button on welcome screen)")
  private val MISC_PROJECT_RENAMED: EventId = GROUP.registerEvent("misc.project.renamed", "Misc project renamed")

  override fun getGroup(): EventLogGroup = GROUP

  fun projectCreated() {
    MISC_PROJECT_CREATED.log()
  }

  fun projectRenamed() {
    MISC_PROJECT_RENAMED.log()
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import org.jetbrains.annotations.ApiStatus

/**
 * Logs misc project creation and renaming to FUS
 */
@ApiStatus.Internal
object MiscProjectUsageCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("pycharm.misc.project", 4)

  private val MISC_FILE_TYPE = EventFields.StringValidatedByCustomRule(
    "file_type",
    MiscProjectUsageCollectorValidationRule::class.java,
    "Misc project created (user clicked on the button on welcome screen"
  )

  private val MISC_PROJECT_CREATED: VarargEventId = GROUP.registerVarargEvent("misc.project.created", MISC_FILE_TYPE)
  private val MISC_PROJECT_RENAMED: EventId = GROUP.registerEvent("misc.project.renamed", "Misc project renamed")

  override fun getGroup(): EventLogGroup = GROUP

  fun projectCreated(fileType: MiscFileType) {
    MISC_PROJECT_CREATED.log(MISC_FILE_TYPE.with(fileType.technicalNameForStatistics))
  }

  fun projectRenamed() {
    MISC_PROJECT_RENAMED.log()
  }
}

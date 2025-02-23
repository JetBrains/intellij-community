// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

internal object IcsActionsLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("settings.repository", 2)
  private val SYNC_SETTINGS = GROUP.registerEvent("sync.settings",
                                                  EventFields.Enum<SyncType>("sync_type") { StringUtil.toLowerCase(it.name) })

  fun logSettingsSync(project: Project?, type: SyncType) {
    SYNC_SETTINGS.log(project, type)
  }
}
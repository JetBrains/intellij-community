// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.org.jetbrains.settingsRepository

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.settingsRepository.SyncType

object IcsActionsLogger {
  fun logSettingsSync(project: Project?, type: SyncType) {
    val data = FeatureUsageData().addData("sync_type", StringUtil.toLowerCase(type.name))
    FUCounterUsageLogger.getInstance().logEvent(project, "settings.repository", "sync.settings", data)
  }
}
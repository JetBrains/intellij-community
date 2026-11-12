// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class MarkdownSettingsCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("markdown.settings", 1)
  private val PREVIEW_MODE = GROUP.registerEvent("preview.mode", EventFields.Enum<MarkdownPreviewMode>("mode"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> = setOf(PREVIEW_MODE.metric(MarkdownSettings.getInstance().previewMode))
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileExtensionValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByCustomRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lsp.ui.settings.LspServerConfiguration
import com.intellij.lsp.ui.settings.LspServerSettings
import com.intellij.openapi.project.Project

internal class LspServerUsagesCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("lspserver", 1)
  private val LSP_SERVER_CONFIGURED = GROUP.registerEvent("lsp.server.configured",
                                                          EventFields.Boolean("enabled"),
                                                          StringListValidatedByCustomRule("file_extensions", FileExtensionValidationRule::class.java),
                                                          EventFields.Enum<LspServerConfiguration.CommunicationMode>("communication_mode") { it.name.lowercase() })


  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val metrics = mutableSetOf<MetricEvent>()

    val settings = LspServerSettings.getInstance(project)
    settings.servers.forEach { server ->
      metrics.add(LSP_SERVER_CONFIGURED.metric(
        server.enabled,
        server.getFileExtensions(),
        server.communicationMode,
      ))
    }

    return metrics
  }
}
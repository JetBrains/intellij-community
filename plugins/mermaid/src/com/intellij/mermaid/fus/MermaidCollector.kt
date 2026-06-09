// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType

internal object MermaidCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = group

  private val group = EventLogGroup("mermaid.count", 2)

  private val diagramUsed = group.registerEvent(
    "diagram.used",
    EventFields.Enum<DiagramType>("type"),
    EventFields.FileType
  )

  fun reportDiagramUsed(type: DiagramType, file: FileType) {
    diagramUsed.log(type, file)
  }
}

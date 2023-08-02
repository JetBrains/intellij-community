package com.intellij.mermaid.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType

internal class MermaidCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return Companion.group
  }

  companion object {
    private val group = EventLogGroup("mermaid.count", 1)

    private val diagramUsed = group.registerEvent(
      "diagram.used",
      EventFields.Enum<DiagramType>("type"),
      EventFields.FileType
    )

    private val diagramsInjected = group.registerEvent(
      "diagrams.injected",
      EventFields.StringList("types", DiagramType.values().map { it.name }),
      EventFields.FileType,
      EventFields.Count
    )

    fun reportDiagramUsed(type: DiagramType, file: FileType) {
      diagramUsed.log(type, file)
    }

    fun reportInjectedDiagrams(types: List<DiagramType>, file: FileType, count: Int) {
      diagramsInjected.log(types.map { it.name }, file, count)
    }
  }
}

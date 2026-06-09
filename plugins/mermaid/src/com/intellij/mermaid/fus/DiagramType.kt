// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.fus

import com.intellij.mermaid.lang.lexer.MermaidToken
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.traverse
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType

internal enum class DiagramType(private val elementType: MermaidToken) {
  Pie(MermaidTokens.Pie.PIE),
  Journey(MermaidTokens.Journey.JOURNEY),
  Flowchart(MermaidTokens.Flowchart.FLOWCHART),
  Sequence(MermaidTokens.Sequence.SEQUENCE),
  Class(MermaidTokens.ClassDiagram.CLASS_DIAGRAM),
  State(MermaidTokens.StateDiagram.STATE_DIAGRAM),
  EntityRelationship(MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP),
  Gantt(MermaidTokens.Gantt.GANTT),
  Requirement(MermaidTokens.Requirement.REQUIREMENT_DIAGRAM),
  GitGraph(MermaidTokens.GitGraph.GIT_GRAPH),
  C4(MermaidTokens.C4.C4_CONTEXT),
  Mindmap(MermaidTokens.Mindmap.MINDMAP),
  Timeline(MermaidTokens.Timeline.TIMELINE),
  Quadrant(MermaidTokens.Quadrant.QUADRANT),
  ZenUml(MermaidTokens.ZenUML.ZEN_UML),
  Sankey(MermaidTokens.Sankey.SANKEY);

  companion object {
    fun from(elementType: IElementType): DiagramType? {
      return values().find { it.elementType == elementType }
    }
  }
}

internal fun MermaidFile.obtainDiagramType(): DiagramType? {
  val types = traverse().mapNotNull { it.elementType }
  return types.mapNotNull(DiagramType::from).firstOrNull()
}

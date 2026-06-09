// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.mermaid.lang.psi.MermaidSubgraphStatement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

class MermaidDiagramCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val diagrams =
    listOf(
      "pie",
      "journey",
      "flowchart",
      "sequenceDiagram",
      "classDiagram",
      "stateDiagram",
      "stateDiagram-v2",
      "erDiagram",
      "gantt",
      "requirementDiagram",
      "gitGraph",
      "C4Context",
      "C4Container",
      "C4Component",
      "C4Dynamic",
      "C4Deployment",
      "mindmap",
      "quadrantChart",
      "timeline",
      "zenuml",
      "sankey-beta",
      "xychart-beta",
      "block-beta",
    )

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    result.addAllElements(diagrams.map { d ->
      LookupElementBuilder
        .create(d)
        .withCaseSensitivity(false)
    })
  }
}

class TitleCompletionProvider : MermaidSimpleCompletionProvider(listOf("title"))

class BranchCompletionProvider(private val branch: String) :
  MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, branch))
  }
}

open class MermaidSimpleCompletionProvider(private val keywords: List<String>) :
  CompletionProvider<CompletionParameters>() {

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    result.addAllElements(keywords.map {
      LookupElementBuilder
        .create(it)
        .withCaseSensitivity(false)
    })
  }
}

class FlowchartCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, "subgraph"))

    val psiElement = parameters.position
    psiElement.parentOfType<MermaidSubgraphStatement>()?.let {
      result.addElement(
        LookupElementBuilder
          .create("direction")
          .withCaseSensitivity(false)
      )
    }
  }
}

class ExtendedDirectionCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v"))

class DirectionCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("LR", "RL", "TB", "BT"))

class PieShowDataCompletionProvider : MermaidSimpleCompletionProvider(listOf("showData"))

class SequenceBlockCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("loop", "alt", "opt", "par", "par_over", "rect", "critical", "break", "box")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class SequenceActorParticipantCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("participant", "actor")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class SequenceSimpleCompletionProvider(string: String) : MermaidSimpleCompletionProvider(listOf(string))

class ClassDiagramSimpleCompletionProvider : MermaidSimpleCompletionProvider(listOf("class", "direction", "style"))

class ClassDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("interface", "abstract", "service", "enumeration"))

class ClassDiagramLiveTemplateCompletionProvider(private val keyword: String) :
  MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, keyword))
  }
}

class StateDiagramLiveTemplateCompletionProvider(private val keyword: String) :
  MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, keyword))
  }
}

class StateDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("choice", "fork", "join"))

class GanttTopLevelCompletionProvider :
  MermaidSimpleCompletionProvider(
    listOf(
      "dateFormat",
      "excludes",
      "includes",
      "axisFormat"
    )
  )

class GanttSimpleCompletionProvider :
  MermaidSimpleCompletionProvider(
    listOf(
      "done",
      "active",
      "crit",
      "after",
      "milestone"
    )
  )

class RequirementCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf(
    "requirement",
    "functionalRequirement",
    "interfaceRequirement",
    "performanceRequirement",
    "physicalRequirement",
    "designConstraint",
    "element"
  )

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class RequirementRiskCompletionProvider : MermaidSimpleCompletionProvider(listOf("high", "medium", "low"))

class RequirementVerifyMethodCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("analysis", "inspection", "test", "demonstration"))

class RequirementRelationshipCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("contains", "copies", "derives", "satisfies", "verifies", "refines", "traces"))

class GitGraphSimpleCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("commit", "branch", "checkout", "merge", "cherry-pick"))

class GitGraphCommitCompletionProvider(private val keywords: List<String>) : MermaidLiveTemplateCompletionProvider() {

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class GitGraphCommitTypeCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("NORMAL", "REVERSE", "HIGHLIGHT"))

class GitGraphDirectionCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("LR", "BT"))


class C4CompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val simpleKeywords = listOf(
    "Person_Ext",
    "Person",
    "SystemQueue_Ext",
    "SystemDb_Ext",
    "System_Ext",
    "SystemQueue",
    "SystemDb",
    "System",

    "ContainerQueue_Ext",
    "ContainerDb_Ext",
    "Container_Ext",
    "ContainerQueue",
    "ContainerDb",
    "Container",

    "ComponentQueue_Ext",
    "ComponentDb_Ext",
    "Component_Ext",
    "ComponentQueue",
    "ComponentDb",
    "Component",

    "Deployment_Node",
    "Node",
    "Node_L",
    "Node_R",

    "Rel",
    "BiRel",
    "Rel_Up",
    "Rel_U",
    "Rel_Down",
    "Rel_D",
    "Rel_Left",
    "Rel_L",
    "Rel_Right",
    "Rel_R",
    "Rel_Back",
    "RelIndex",

    "UpdateElementStyle",
    "UpdateRelStyle",
    "UpdateLayoutConfig"
  )

  private val boundaryKeywords = listOf(
    "Boundary",
    "Enterprise_Boundary",
    "System_Boundary",
    "Container_Boundary"
  )

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(simpleKeywords.map {
      createKeywordLookupElement(
        project,
        keyword = "c4_simple",
        predefinedNameVar = it
      )
    })
    result.addAllElements(boundaryKeywords.map {
      createKeywordLookupElement(
        project,
        keyword = "c4_boundary",
        predefinedNameVar = it
      )
    })
  }
}


class QuadrantCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("x-axis", "y-axis", "quadrant-1", "quadrant-2", "quadrant-3", "quadrant-4"))


class XYChartCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("x-axis", "y-axis", "bar", "line"))

class XYChartOrientationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("horizontal", "vertical"))


class BlockCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("block")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

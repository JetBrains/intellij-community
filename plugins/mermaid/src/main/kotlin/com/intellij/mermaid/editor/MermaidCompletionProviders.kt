package com.intellij.mermaid.editor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.mermaid.lang.psi.impl.MermaidSubgraphStatementImpl
import com.intellij.psi.PsiFile
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
      "C4Deployment"
    )

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    parameters.position.parentOfType<PsiFile>()?.let {
      result.addAllElements(diagrams.map { d ->
        LookupElementBuilder
          .create(d)
          .withCaseSensitivity(false)
      })
    }
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

    var psiElement = parameters.position
    while (psiElement.parent != null) {
      psiElement = psiElement.parent
      if (psiElement is MermaidSubgraphStatementImpl) {
        result.addElement(
          LookupElementBuilder
            .create("direction")
            .withCaseSensitivity(false)
        )
        break
      }
    }
  }
}

class DirectionCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v"))

class PieShowDataCompletionProvider : MermaidSimpleCompletionProvider(listOf("showData"))

class SequenceCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("loop", "alt", "opt", "par", "rect", "critical", "break", "participant", "actor")

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

class ClassDiagramSimpleCompletionProvider : MermaidSimpleCompletionProvider(listOf("class", "direction"))

class ClassDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("interface", "abstract", "service", "enumeration"))

class StateDiagramSimpleCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("state", "direction", "as", "note", "end"))

class StateDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("choice", "fork", "join"))

class GanttSimpleCompletionProvider :
  MermaidSimpleCompletionProvider(
    listOf(
      "dateFormat",
      "excludes",
      "includes",
      "done",
      "active",
      "crit",
      "after",
      "milestone",
      "axisFormat"
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

class GitGraphCommitCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf(
    "id",
    "type",
    "tag",
    "msg"
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

class GitGraphCommitTypeCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("NORMAL", "REVERSE", "HIGHLIGHT"))


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

package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidSubgraphStatementImpl
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

class MermaidDiagramCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val diagrams =
    listOf("pie", "journey", "flowchart", "sequenceDiagram", "classDiagram", "stateDiagram", "stateDiagram-v2")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    parameters.position.parentOfType<PsiFile>()?.let {
      result.addAllElements(diagrams.map { d -> createKeywordLookupElement(d) })
    }
  }

  private fun createKeywordLookupElement(keyword: String): LookupElement {
    val insertHandler = InsertHandler { ctx: InsertionContext, _: LookupElement? ->
      val editor = ctx.editor
      val document = editor.document
      // attempt to fix bug with completion of first element in document
      val lineStartOffset = document.getLineStartOffset(document.getLineNumber(ctx.startOffset))
      document.deleteString(lineStartOffset, ctx.tailOffset)
      EditorModificationUtil.insertStringAtCaret(editor, keyword)
    }
    return LookupElementBuilder
      .create(keyword)
      .withInsertHandler(insertHandler)
  }
}

class TitleCompletionProvider(diagram: IElementType, document: Class<out PsiElement>) :
  CompletionProvider<CompletionParameters>() {

  private val pattern = PlatformPatterns.psiElement().withParent(
    PlatformPatterns.psiElement().afterSiblingSkipping(
      PlatformPatterns.not(PlatformPatterns.psiElement(diagram))
        .andOr(PlatformPatterns.not(PlatformPatterns.psiElement(document))),
      PlatformPatterns.or(PlatformPatterns.psiElement(diagram), PlatformPatterns.psiElement(document))
    )
  )

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val element = parameters.position
    if (pattern.accepts(element)) {
      result.addElement(LookupElementBuilder.create("title"))
    }
  }
}

class BranchCompletionProvider(private val branch: String) :
  MermaidLiveTemplateCompletionProvider(deleteIndent = true) {
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
    result.addAllElements(keywords.map { LookupElementBuilder.create(it) })
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
        result.addElement(LookupElementBuilder.create("direction"))
        break
      }
    }
  }
}

class DirectionCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v"))

class PieShowDataCompletionProvider : MermaidSimpleCompletionProvider(listOf("showData"))

class SequenceCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("loop", "alt", "opt", "par", "rect")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class ClassDiagramSimpleCompletionProvider : MermaidSimpleCompletionProvider(listOf("class", "direction"))

class ClassDiagramCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("<<", "~")
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class ClassDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("interface", "abstract", "service", "enumeration"))

class StateDiagramSimpleCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("state", "direction", "as", "note", "end"))

class StateDiagramAnnotationCompletionProvider :
  MermaidSimpleCompletionProvider(listOf("choice", "fork", "join"))

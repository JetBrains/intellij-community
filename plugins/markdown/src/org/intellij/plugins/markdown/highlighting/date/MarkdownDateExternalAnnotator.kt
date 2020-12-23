// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.date

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.SmartList
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import java.util.concurrent.atomic.AtomicBoolean

class MarkdownDateExternalAnnotator : ExternalAnnotator<MyDocumentInfo, MyAnnotationResult>() {

  override fun collectInformation(file: PsiFile): MyDocumentInfo? {
    val document = PsiDocumentManager.getInstance(file.project)
                     .getDocument(file) ?: return null
    return MyDocumentInfo(document, file)
  }

  override fun doAnnotate(documentInfo: MyDocumentInfo?): MyAnnotationResult? {
    val (document, file) = documentInfo ?: return null

    tryRegisterDocumentListener(document)

    val listenedChanges = document.getUserData(MarkdownDateDocumentListener.CHANGED_RANGES)
    document.putUserData(MarkdownDateDocumentListener.CHANGED_RANGES, null)

    val elementsToVisit = listenedChanges?.flatMap {
      file.findElementsIntersectingRange(it)
    }?.toSet() ?: setOf(file)

    val visitor = MarkdownTextsCollectingVisitor()
    for (element in elementsToVisit) {
      visitor.visitElement(element)
    }

    val dateRanges = findDates(visitor.texts.map(TextWithOffset::first))
      .flatMapIndexed { i: Int, ranges: Collection<TextRange> ->
        ranges.map { it.shiftRight(visitor.texts[i].second) }
      }.toSet()

    return MyAnnotationResult(document, dateRanges, resetCache = listenedChanges == null)
  }

  override fun apply(file: PsiFile, annotationResult: MyAnnotationResult?, holder: AnnotationHolder) {
    val (document, ranges, resetCache) = annotationResult ?: return

    val markupModel = DocumentMarkupModel.forDocument(document, file.project, true)

    ApplicationManager.getApplication().invokeLater {
      if (resetCache) {
        markupModel.removeAllHighlighters()
      }
      for (range in ranges) {
        val highlighter = markupModel.addRangeHighlighter(MarkdownHighlighterColors.DATE,
                                                          range.startOffset, range.endOffset,
                                                          HighlighterLayer.ADDITIONAL_SYNTAX,
                                                          HighlighterTargetArea.EXACT_RANGE)
        highlighter.putUserData(IS_DATE_HIGHLIGHTER, true)
      }
    }
  }

  private fun tryRegisterDocumentListener(document: Document) {
    val dataHolder = document as? UserDataHolderEx ?: return

    val hasDocumentListener = dataHolder.putUserDataIfAbsent(HAS_DOCUMENT_LISTENER, AtomicBoolean(false))
    if (hasDocumentListener.compareAndSet(false, true)) {
      document.addDocumentListener(MarkdownDateDocumentListener)
    }
  }
}

data class MyDocumentInfo(val document: Document, val file: PsiFile)
data class MyAnnotationResult(val document: Document, val ranges: Set<TextRange>, val resetCache: Boolean)

val IS_DATE_HIGHLIGHTER = Key.create<Boolean>("MARKDOWN_DATE_EXTERNAL_ANNOTATOR_IS_DATE_HIGHLIGHTER")
private val HAS_DOCUMENT_LISTENER = Key.create<AtomicBoolean>("MARKDOWN_DATE_EXTERNAL_ANNOTATOR_HAS_DOCUMENT_LISTENER")

private fun PsiFile.findElementsIntersectingRange(range: TextRange): Iterable<PsiElement> {
  var startOffset = minOf(range.startOffset - 1, 0)
  val elements = SmartList<PsiElement>()

  while (startOffset <= range.endOffset) {
    val element = findElementAt(startOffset)

    if (element == null) {
      startOffset++
      continue
    }

    elements += element
    startOffset = element.endOffset
  }

  return elements
}

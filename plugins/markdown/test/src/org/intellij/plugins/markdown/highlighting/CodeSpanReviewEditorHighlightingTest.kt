// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter
import org.intellij.plugins.markdown.lang.MarkdownFileType

/**
 * Guards the code-span color in the review-comment Markdown editor.
 *
 * The code-span content color comes only from the daemon annotator. The review editor uses a non-physical
 * file, which the daemon does not invalidate on an edit, so the color would go stale. The
 * INTERACTIVE_NON_PHYSICAL_DOCUMENT marker, which CodeReviewMarkdownEditor sets, opts the document into an
 * editor's invalidation and keeps the color. This test drives the production incremental daemon.
 */
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
class CodeSpanReviewEditorHighlightingTest : ProductionDaemonAnalyzerTestCase() {

  private val symbolSeverity = HighlightInfoType.SYMBOL_TYPE_SEVERITY

  fun testLightEditorWithMarkerKeepsCodeSpanColorAfterEdit() {
    // Mirror CodeReviewMarkdownEditor.create: a raw text-field editor over a non-physical Dummy.md file.
    val psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy.md", MarkdownFileType.INSTANCE, "`code` tail", LocalTimeCounter.currentTime(), true, false)
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
    document.putUserData(DaemonCodeAnalyzer.INTERACTIVE_NON_PHYSICAL_DOCUMENT, project)

    val editor = EditorFactory.getInstance()
      .createEditor(document, project, psiFile.viewProvider.virtualFile, false, EditorKind.UNTYPED) as EditorEx
    EditorTextField.setupTextFieldEditor(editor)
    Disposer.register(testRootDisposable, Disposable { EditorFactory.getInstance().releaseEditor(editor) })

    editor.caretModel.moveToOffset(document.textLength)
    EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val before = myTestDaemonCodeAnalyzer.waitHighlighting(psiFile, symbolSeverity)
    assertTrue("Code span must be colored initially", isCodeSpanColored(before, document.text))

    // Type a character at the end, outside the backticks.
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(document.textLength, "x")
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    editor.caretModel.moveToOffset(document.textLength)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val after = myTestDaemonCodeAnalyzer.waitHighlighting(psiFile, symbolSeverity)

    assertTrue("Code span must stay colored after an edit outside it", isCodeSpanColored(after, document.text))
  }

  private fun isCodeSpanColored(infos: List<HighlightInfo>, text: String): Boolean {
    val start = text.indexOf("code")
    val end = start + "code".length
    return infos.any {
      it.forcedTextAttributesKey == MarkdownHighlighterColors.CODE_SPAN && it.startOffset <= start && it.endOffset >= end
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.ProductionDaemonTestUtil
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the code-span color in the review-comment Markdown editor.
 *
 * The code-span content color comes only from the daemon annotator. The review editor uses a non-physical
 * file, which the daemon does not invalidate on an edit, so the color would go stale. The
 * INTERACTIVE_NON_PHYSICAL_DOCUMENT marker, which CodeReviewMarkdownEditor sets, opts the document into an
 * editor's invalidation and keeps the color. This test drives the production incremental daemon.
 */
@TestApplication
class CodeSpanReviewEditorHighlightingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val symbolSeverity = HighlightInfoType.SYMBOL_TYPE_SEVERITY

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun testLightEditorWithMarkerKeepsCodeSpanColorAfterEdit(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    configureInspections(emptyArray(), project, disposable)
    val testDaemonCodeAnalyzer = TestDaemonCodeAnalyzerImpl(project)
    testDaemonCodeAnalyzer.prepareForTest()
    DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = false
    try {
      val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
      withContext(Dispatchers.EDT) {
        ProductionDaemonTestUtil.runTestInProduction(false, daemonCodeAnalyzer) {
          checkCodeSpanColorAfterEdit(project, testDaemonCodeAnalyzer)
        }
      }
    }
    finally {
      DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = true
      testDaemonCodeAnalyzer.cleanupAfterTest()
    }
  }

  @RequiresEdt
  private fun checkCodeSpanColorAfterEdit(project: Project, testDaemonCodeAnalyzer: TestDaemonCodeAnalyzerImpl) {
    // Mirror CodeReviewMarkdownEditor.create: a raw text-field editor over a non-physical Dummy.md file.
    val psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy.md", MarkdownFileType.INSTANCE, "`code` tail", LocalTimeCounter.currentTime(), true, false)
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
    document.putUserData(DaemonCodeAnalyzer.INTERACTIVE_NON_PHYSICAL_DOCUMENT, project)

    val editor = EditorFactory.getInstance()
      .createEditor(document, project, psiFile.viewProvider.virtualFile, false, EditorKind.UNTYPED) as EditorEx
    try {
      EditorTextField.setupTextFieldEditor(editor)

      editor.caretModel.moveToOffset(document.textLength)
      EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      val before = testDaemonCodeAnalyzer.waitHighlighting(psiFile, symbolSeverity)
      assertTrue(isCodeSpanColored(before, document.text), "Code span must be colored initially")

      // Type a character at the end, outside the backticks.
      WriteCommandAction.runWriteCommandAction(project) {
        document.insertString(document.textLength, "x")
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      editor.caretModel.moveToOffset(document.textLength)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val after = testDaemonCodeAnalyzer.waitHighlighting(psiFile, symbolSeverity)

      assertTrue(isCodeSpanColored(after, document.text), "Code span must stay colored after an edit outside it")
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }

  private fun isCodeSpanColored(infos: List<HighlightInfo>, text: String): Boolean {
    val start = text.indexOf("code")
    val end = start + "code".length
    return infos.any {
      it.hasTextAttributesKey(MarkdownHighlighterColors.CODE_SPAN) &&
      it.startOffset <= start && it.endOffset >= end
    }
  }
}

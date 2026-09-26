package com.intellij.sh.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ShHighlightUsagesInMarkdownTest : BasePlatformTestCase() {

  override fun getTestDataPath(): String = PluginPathManager.getPluginHomePath("sh") + "/core/testData/highlight_usages/"

  fun testHighlightUsagesInMarkdownCodeFence() {
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project) {
      myFixture.setReadEditorMarkupModel(true)
      myFixture.configureByFile("highlightUsagesInMarkdown.md")
      myFixture.doHighlighting()
      val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
      val actualHighlighters = getIdentifierHighlighters(hostEditor)
      val actualHighlighterStrings = stringify(actualHighlighters, hostEditor)
      UsefulTestCase.assertSameElements(actualHighlighterStrings, listOf("4:1/5 reset", "5:1/5 reset"))
    }
  }

  fun testManualHighlightUsagesInFileInMarkdownCodeFence() {
    myFixture.configureByFile("highlightUsagesInMarkdown.md")
    myFixture.performEditorAction(IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE)

    val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
    val document = hostEditor.document
    val actualStrings = hostEditor.markupModel.getAllHighlighters()
      .filter { it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES }
      .sortedBy { it.startOffset }
      .map {
        val lineNumber = document.getLineNumber(it.startOffset)
        val columnNumber = it.startOffset - document.getLineStartOffset(lineNumber)
        "${lineNumber + 1}:${columnNumber + 1}/${it.endOffset - it.startOffset} ${document.getText(it.textRange)}"
      }
    UsefulTestCase.assertSameElements(actualStrings, listOf("4:1/5 reset", "5:1/5 reset"))
  }

  private fun getIdentifierHighlighters(editor: Editor): List<HighlightInfo> {
    return editor.markupModel.getAllHighlighters().asSequence()
      .mapNotNull { HighlightInfo.fromRangeHighlighter(it) }
      .filter { it.severity === HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY }
      .toList()
  }

  private fun stringify(highlighters: List<Segment>, editor: Editor): List<String> {
    val document = editor.document
    return highlighters.sortedBy { it.startOffset }
      .map {
        val range = TextRange(it.startOffset, it.endOffset)
        val lineNumber = document.getLineNumber(range.startOffset)
        val columnNumber = range.startOffset - document.getLineStartOffset(lineNumber)
        "${lineNumber + 1}:${columnNumber + 1}/${range.length} ${document.getText(range)}"
      }
  }
}

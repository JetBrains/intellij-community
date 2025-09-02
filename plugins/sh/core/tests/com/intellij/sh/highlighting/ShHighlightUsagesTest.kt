package com.intellij.sh.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.sh.psi.ShFile
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ShHighlightUsagesTest : BasePlatformTestCase() {

  override fun getTestDataPath(): String = PluginPathManager.getPluginHomePath("sh") + "/core/testData/highlight_usages/"

  fun testCallCommands() {
    doTest("3:5/5 reset", "4:5/5 reset")
    suppressOccurrences(testRootDisposable)
    doTest()
  }

  fun testCallFunction() {
    doTest("2:10/7 my_func", "6:1/7 my_func")
    suppressOccurrences(testRootDisposable)
    doTest("2:10/7 my_func", "6:1/7 my_func")
  }

  private fun doTest(vararg expectedHighlighting: String) {
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project) {
      myFixture.setReadEditorMarkupModel(true)
      myFixture.configureByFile(getTestName(true) + ".sh")
      myFixture.doHighlighting()
      val actualHighlighters = getIdentifierHighlighters(myFixture.editor)
      val actualHighlighterStrings = stringify(actualHighlighters, myFixture.editor)
      UsefulTestCase.assertSameElements(actualHighlighterStrings, expectedHighlighting.toList())
    }
  }

  private fun getIdentifierHighlighters(editor: Editor): List<HighlightInfo> {
    return editor.getMarkupModel().getAllHighlighters().asSequence()
      .mapNotNull { HighlightInfo.fromRangeHighlighter(it) }
      .filter { it.severity === HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY }
      .toList()
  }

  private fun stringify(highlighters: List<Segment>, editor: Editor): List<String> {
    val document = editor.document
    return highlighters.sortedBy { it.getStartOffset() }
      .map {
        val range = TextRange(it.getStartOffset(), it.getEndOffset())
        val lineNumber = document.getLineNumber(range.startOffset)
        val columnNumber = range.startOffset - document.getLineStartOffset(lineNumber)
        "${lineNumber + 1}:${columnNumber + 1}/${range.length} ${document.getText(range)}"
      }
  }

  companion object {
    @JvmStatic
    fun suppressOccurrences(testRootDisposable: Disposable) {
      val alwaysSuppressed = object : ShOccurrencesHighlightingSuppressor {
        override fun suppressOccurrencesHighlighting(editor: Editor, file: ShFile): Boolean = true
      }
      maskExtensions(ShOccurrencesHighlightingSuppressor.EP_NAME, listOf(alwaysSuppressed), testRootDisposable)
    }
  }
}
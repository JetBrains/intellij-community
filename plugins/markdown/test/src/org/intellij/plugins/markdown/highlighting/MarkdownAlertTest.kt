// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil

class MarkdownAlertTest : BasePlatformTestCase() {

  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/html/alerts"

  fun testNoteAlertTitleColorApplied() = doHighlightingTest("[!NOTE]", MarkdownHighlighterColors.ALERT_TITLE_NOTE)
  fun testTipAlertTitleColorApplied() = doHighlightingTest("[!TIP]", MarkdownHighlighterColors.ALERT_TITLE_TIP)
  fun testImportantAlertTitleColorApplied() = doHighlightingTest("[!IMPORTANT]", MarkdownHighlighterColors.ALERT_TITLE_IMPORTANT)
  fun testWarningAlertTitleColorApplied() = doHighlightingTest("[!WARNING]", MarkdownHighlighterColors.ALERT_TITLE_WARNING)
  fun testCautionAlertTitleColorApplied() = doHighlightingTest("[!CAUTION]", MarkdownHighlighterColors.ALERT_TITLE_CAUTION)

  fun testNoteAlertGutterIconPresent() = doGutterIconTest("[!NOTE]", AllIcons.General.BalloonInformation)
  fun testTipAlertGutterIconPresent() = doGutterIconTest("[!TIP]", AllIcons.Actions.IntentionBulb)
  fun testImportantAlertGutterIconPresent() = doGutterIconTest("[!IMPORTANT]", AllIcons.General.Balloon)
  fun testWarningAlertGutterIconPresent() = doGutterIconTest("[!WARNING]", AllIcons.General.BalloonWarning)
  fun testCautionAlertGutterIconPresent() = doGutterIconTest("[!CAUTION]", AllIcons.General.BalloonError)

  fun testAlertRenderedAsBlockquoteInPreview() = doPreviewTest()
  fun testAlertNotRenderedAsGitHubAlertMarkupInPreview() = doPreviewTest()
  fun testMultipleAlertsEachRenderedAsBlockquoteInPreview() = doPreviewTest()

  private fun doPreviewTest() {
    val expectedHtml = myFixture.configureByFile("${getTestName(true)}.html").text
    val mdFile = myFixture.configureByFile("${getTestName(true)}.md")
    assertEquals(expectedHtml.trim(), MarkdownUtil.generateMarkdownHtml(mdFile.virtualFile, mdFile.text, project).trim())
  }

  private fun doHighlightingTest(title: String, expectedKey: com.intellij.openapi.editor.colors.TextAttributesKey) {
    myFixture.configureByText("test.md", "> $title\n> Content")
    val highlights = myFixture.doHighlighting()
    val offset = myFixture.file.text.indexOf(title)
    assertTrue(
      "Expected $title to be highlighted with $expectedKey",
      highlights.any { it.forcedTextAttributesKey == expectedKey && it.startOffset == offset && it.endOffset == offset + title.length }
    )
  }

  private fun doGutterIconTest(title: String, expectedIcon: javax.swing.Icon) {
    myFixture.configureByText("test.md", "> $title\n> Content")
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    assertNotNull("Expected gutter icon $expectedIcon for $title", markers.firstOrNull { it.icon == expectedIcon })
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/html/alerts")
class MarkdownAlertTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testNoteAlertTitleColorApplied(): Unit = doHighlightingTest("[!NOTE]", MarkdownHighlighterColors.ALERT_TITLE_NOTE)
  @Test
  fun testTipAlertTitleColorApplied(): Unit = doHighlightingTest("[!TIP]", MarkdownHighlighterColors.ALERT_TITLE_TIP)
  @Test
  fun testImportantAlertTitleColorApplied(): Unit = doHighlightingTest("[!IMPORTANT]", MarkdownHighlighterColors.ALERT_TITLE_IMPORTANT)
  @Test
  fun testWarningAlertTitleColorApplied(): Unit = doHighlightingTest("[!WARNING]", MarkdownHighlighterColors.ALERT_TITLE_WARNING)
  @Test
  fun testCautionAlertTitleColorApplied(): Unit = doHighlightingTest("[!CAUTION]", MarkdownHighlighterColors.ALERT_TITLE_CAUTION)

  @Test
  fun testNoteAlertGutterIconPresent(): Unit = doGutterIconTest("[!NOTE]", AllIcons.General.BalloonInformation)
  @Test
  fun testTipAlertGutterIconPresent(): Unit = doGutterIconTest("[!TIP]", AllIcons.Actions.IntentionBulb)
  @Test
  fun testImportantAlertGutterIconPresent(): Unit = doGutterIconTest("[!IMPORTANT]", AllIcons.General.Balloon)
  @Test
  fun testWarningAlertGutterIconPresent(): Unit = doGutterIconTest("[!WARNING]", AllIcons.General.BalloonWarning)
  @Test
  fun testCautionAlertGutterIconPresent(): Unit = doGutterIconTest("[!CAUTION]", AllIcons.General.BalloonError)

  @Test
  fun testNoteAlertRenderedInPreview(): Unit = doPreviewTest()
  @Test
  fun testWarningAlertRenderedInPreview(): Unit = doPreviewTest()
  @Test
  fun testMultipleAlertsRenderedInPreview(): Unit = doPreviewTest()

  private fun doPreviewTest() {
    val expectedFile = myFixture.configureByFile("$testName.html")
    val expectedHtml = timeoutRunBlocking { readAction { expectedFile.text } }
    val mdFile = myFixture.configureByFile("$testName.md")
    val actualHtml = timeoutRunBlocking { readAction { MarkdownUtil.generateMarkdownHtml(mdFile.virtualFile, mdFile.text, myFixture.project) } }
    assertEquals(expectedHtml.trim(), actualHtml.trim())
  }

  private fun doHighlightingTest(title: String, expectedKey: com.intellij.openapi.editor.colors.TextAttributesKey) {
    myFixture.configureByText("test.md", "> $title\n> Content")
    val highlights = myFixture.doHighlighting()
    val offset = timeoutRunBlocking { readAction { myFixture.file.text } }.indexOf(title)
    assertTrue(
      highlights.any {
        it.hasTextAttributesKey(expectedKey) &&
        it.startOffset == offset && it.endOffset == offset + title.length
      },
      "Expected $title to be highlighted with $expectedKey"
    )
  }

  private fun doGutterIconTest(title: String, expectedIcon: javax.swing.Icon) {
    myFixture.configureByText("test.md", "> $title\n> Content")
    myFixture.doHighlighting()
    val markers = timeoutRunBlocking { readAction { getDisplayedLineMarkers(myFixture.editor.document, myFixture.project) } }
    assertEquals(1, markers.count { it.icon == expectedIcon }, "Expected exactly one gutter icon $expectedIcon for $title")
  }
}

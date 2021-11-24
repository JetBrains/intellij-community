// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings

open class MarkdownFormatterTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/formatter/"
  }

  private var myOldWrap = false
  private var myOldMargin = 0
  private var myOldWrapTextBlocksIfLong = false
  private var myOldKeepLineBreaks = true

  override fun setUp() {
    super.setUp()

    val settings = CodeStyle.getSettings(project)
    val common = settings.getCommonSettings(MarkdownLanguage.INSTANCE)
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)

    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN
    myOldMargin = common.RIGHT_MARGIN
    myOldWrapTextBlocksIfLong = custom.WRAP_TEXT_IF_LONG
    myOldKeepLineBreaks = custom.KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS

    custom.WRAP_TEXT_IF_LONG = true
    custom.KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = false
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true
    common.RIGHT_MARGIN = 40
  }

  override fun tearDown() {
    val settings = CodeStyle.getSettings(project)
    val common = settings.getCommonSettings(MarkdownLanguage.INSTANCE)
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)

    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap
    common.RIGHT_MARGIN = myOldMargin
    custom.WRAP_TEXT_IF_LONG = myOldWrapTextBlocksIfLong
    custom.KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = myOldKeepLineBreaks

    super.tearDown()
  }

  fun testSmoke() {
    doTest()
  }

  fun testHeaders() {
    doTest()
  }

  fun testParagraphs() {
    doTest()
  }

  fun testLists() {
    doTest()
  }

  //For now alignment of fence parts is not supported
  fun testFences() {
    doTest()
  }

  fun testBlockquotes() {
    doTest()
  }

  fun testCodeblocks() {
    doTest()
  }

  fun testTables() {
    doTest()
  }

  fun testReflow() {
    doTest()
  }

  private fun doTest() {
    val before = getTestName(true) + "_before.md"
    val after = getTestName(true) + "_after.md"

    doReformatTest(before, after)

    //check idempotence of formatter
    doReformatTest(after, after)
  }

  private fun doReformatTest(before: String, after: String) {
    configureByFile(before)
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    checkResultByFile(after)
  }
}

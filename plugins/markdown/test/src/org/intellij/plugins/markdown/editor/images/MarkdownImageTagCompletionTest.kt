// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownImageTagCompletionTest: BasePlatformTestCase() {
  fun `test in empty file`() = doTest()

  fun `test in paragraph`() = doTest()

  fun `test after link`() = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestFileName())
    myFixture.type("<")
    val elements = myFixture.complete(CompletionType.BASIC)
    assertContainsElements(elements.map { it.lookupString }, completionLookupString)
    myFixture.type("i\t")
    myFixture.checkResultByFile(getTestFileNameAfter())
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  private fun getTestFileNameAfter(): String {
    return "${getTestName(false)}.after.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/completion/"
  }

  companion object {
    private const val completionLookupString = "img src=\"\">"
  }
}

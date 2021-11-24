// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownConfigureImageLineMarkersTest: BasePlatformTestCase() {
  private val tempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()

  override fun setUp() {
    super.setUp()
    tempDirFixture.setUp()
  }

  override fun tearDown() {
    try {
      tempDirFixture.tearDown()
    } catch (exception: Throwable) {
      addSuppressedException(exception)
    } finally {
      super.tearDown()
    }
  }

  fun `test markdown`() = doTest(7)

  fun `test composite markdown`() = doTest(3)

  fun `test supported inline html`() = doTest(7)

  fun `test plain text html`() = doTest(2)

  fun `test no markers in codefences`() = doTest(0)

  fun `test no markers in html files`() = doTest(0, "no_markers_in_html_files.html")

  fun `test no markers inside html blocks`() = doTest(0)

  private fun doTest(expectedCount: Int, file: String = getTestFileName()) {
    myFixture.configureByFile(file)
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project)
    assertSize(expectedCount, markers)
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/markers/"
  }
}

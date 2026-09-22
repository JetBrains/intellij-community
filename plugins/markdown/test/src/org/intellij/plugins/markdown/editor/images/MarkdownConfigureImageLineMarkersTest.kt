// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.application.options.editor.GutterIconsConfigurable
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.LineMarkerSettingsImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.images.editor.ConfigureHtmlImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.ConfigureMarkdownImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.ConfigureTextHtmlImageLineMarkerProvider

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

  fun `test image settings have one checkbox per format`() {
    val configurable = GutterIconsConfigurable()
    try {
      configurable.createComponent()
      val providerIds = setOf(
        ConfigureHtmlImageLineMarkerProvider::class.java.name,
        ConfigureMarkdownImageLineMarkerProvider::class.java.name,
        ConfigureTextHtmlImageLineMarkerProvider::class.java.name
      )
      val descriptors = configurable.descriptors.filter { it.id in providerIds }
      assertSize(2, descriptors)
      assertEquals(2, descriptors.map { it.name }.distinct().size)
      assertTrue(descriptors.all { it.icon != null })
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  fun `test HTML image setting controls both HTML forms`() {
    val provider = ConfigureHtmlImageLineMarkerProvider()
    val settings = LineMarkerSettingsImpl.getSettings() as LineMarkerSettingsImpl
    val previousValue = settings.providers[provider.id]
    try {
      settings.setEnabled(provider, false)
      doTest(0, "supported_inline_html.md")
      doTest(0, "plain_text_html.md")
      doTest(7, "markdown.md")
      settings.setEnabled(provider, true)
      doTest(7, "supported_inline_html.md")
      doTest(2, "plain_text_html.md")
    }
    finally {
      if (previousValue == null) settings.resetEnabled(provider)
      else settings.setEnabled(provider, previousValue)
    }
  }

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

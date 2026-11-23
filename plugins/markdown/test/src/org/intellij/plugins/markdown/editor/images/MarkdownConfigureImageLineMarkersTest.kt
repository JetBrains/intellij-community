// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.application.options.editor.GutterIconsConfigurable
import com.intellij.codeInsight.daemon.impl.LineMarkerSettingsImpl
import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.images.editor.ConfigureHtmlImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.ConfigureMarkdownImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.ConfigureTextHtmlImageLineMarkerProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/images/markers")
class MarkdownConfigureImageLineMarkersTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  private val tempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false).trimStart().replace(' ', '_')
    tempDirFixture.setUp()
  }

  @AfterEach
  fun tearDown() {
    tempDirFixture.tearDown()
  }

  @Test
  fun `test markdown`(): Unit = doTest(7)

  @Test
  fun `test composite markdown`(): Unit = doTest(3)

  @Test
  fun `test supported inline html`(): Unit = doTest(7)

  @Test
  fun `test plain text html`(): Unit = doTest(2)

  @Test
  fun `test no markers in codefences`(): Unit = doTest(0)

  @Test
  fun `test no markers in html files`(): Unit = doTest(0, "no_markers_in_html_files.html")

  @Test
  fun `test no markers inside html blocks`(): Unit = doTest(0)

  @Test
  fun `test image settings have one checkbox per format`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val configurable = GutterIconsConfigurable()
      try {
        configurable.createComponent()
        val providerIds = setOf(
          ConfigureHtmlImageLineMarkerProvider::class.java.name,
          ConfigureMarkdownImageLineMarkerProvider::class.java.name,
          ConfigureTextHtmlImageLineMarkerProvider::class.java.name
        )
        val descriptors = configurable.descriptors.filter { it.id in providerIds }
        assertThat(descriptors).hasSize(2)
        assertEquals(2, descriptors.map { it.name }.distinct().size)
        assertTrue(descriptors.all { it.icon != null })
      }
      finally {
        configurable.disposeUIResources()
      }
    }
  }

  @Test
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
    val markers = timeoutRunBlocking { readAction { getDisplayedLineMarkers(myFixture.editor.document, myFixture.project) } }
    assertThat(markers).hasSize(expectedCount)
  }

  private fun getTestFileName(): String {
    return "$testName.md"
  }
}

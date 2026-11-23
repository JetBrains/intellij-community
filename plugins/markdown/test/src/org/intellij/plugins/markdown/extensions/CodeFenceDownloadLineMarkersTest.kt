// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.extensions.common.plantuml.PlantUMLCodeGeneratingProvider
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
abstract class CodeFenceDownloadLineMarkersTest(private val createFakeFiles: Boolean) {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  protected val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @TempDir
  lateinit var tempDirectory: Path

  private lateinit var testName: String

  @BeforeEach
  fun setUpExternalFiles(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false).trimStart().replace(' ', '_')
    myFixture.testDataPath = "${MarkdownTestingUtil.TEST_DATA_PATH}/lineMarkers/"
    ExtensionTestingUtil.mockPathManager(tempDirectory, disposable)
    if (createFakeFiles) {
      val plantUmlExtension = MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<PlantUMLCodeGeneratingProvider>()!!
      ExtensionTestingUtil.createFakeExternalFiles(plantUmlExtension)
    }
  }

  class DownloadAvailable: CodeFenceDownloadLineMarkersTest(createFakeFiles = false)

  class DownloadUnavailable: CodeFenceDownloadLineMarkersTest(createFakeFiles = true) {
    override fun doTest(expectedCount: Int, predicate: (LineMarkerInfo<*>) -> Boolean) {
      // Both extensions should be installed, so there should be no line markers at all
      super.doTest(0, predicate)
    }
  }

  @Test
  fun `plantuml single fence`(): Unit = doTest(1, ::plantumlPredicate)

  @Test
  fun `plantuml multiple fences`(): Unit = doTest(3, ::plantumlPredicate)

  protected fun plantumlPredicate(markerInfo: LineMarkerInfo<*>): Boolean {
    return markerInfo.lineMarkerTooltip == MarkdownBundle.message("markdown.extensions.plantuml.download.line.marker.text")
  }

  protected open fun configureContent() {
    myFixture.configureByFile(getTestFileName())
  }

  protected open fun doTest(expectedCount: Int, predicate: (LineMarkerInfo<*>) -> Boolean) {
    configureContent()
    myFixture.doHighlighting()
    timeoutRunBlocking {
      readAction {
        val allMarkers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
        val markers = allMarkers.filter(predicate)
        assertThat(markers).hasSize(expectedCount)
        for (info in markers) {
          assertEquals(MarkdownTokenTypes.FENCE_LANG, PsiUtilCore.getElementType(info.element))
        }
      }
    }
  }

  protected fun getTestFileName(): String {
    return "$testName.md"
  }
}

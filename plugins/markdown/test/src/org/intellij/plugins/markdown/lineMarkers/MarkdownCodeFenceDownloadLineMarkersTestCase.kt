// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class MarkdownCodeFenceDownloadLineMarkersTestCase: BasePlatformTestCase() {
  protected val tempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()

  fun `test mermaid single empty fence`() = doTest(1, ::mermaidPredicate)

  fun `test mermaid single fence`() = doTest(1, ::mermaidPredicate)

  fun `test mermaid multiple fences`() = doTest(2, ::mermaidPredicate)

  fun `test plantuml single fence`() = doTest(1, ::plantumlPredicate)

  fun `test plantuml multiple fences`() = doTest(3, ::plantumlPredicate)

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

  protected fun withRemappedBaseDirectory(directory: String, block: () -> Unit) {
    val oldDirectory = MarkdownExtensionWithExternalFiles.BASE_DIRECTORY
    MarkdownExtensionWithExternalFiles.BASE_DIRECTORY = directory
    try {
      block()
    } catch (exception: Throwable) {
      addSuppressedException(exception)
    } finally {
      MarkdownExtensionWithExternalFiles.BASE_DIRECTORY = oldDirectory
    }
  }

  protected fun mermaidPredicate(markerInfo: LineMarkerInfo<*>): Boolean {
    return markerInfo.lineMarkerTooltip == MarkdownBundle.message("markdown.extensions.mermaid.download.line.marker.text")
  }

  protected fun plantumlPredicate(markerInfo: LineMarkerInfo<*>): Boolean {
    return markerInfo.lineMarkerTooltip == MarkdownBundle.message("markdown.extensions.plantuml.download.line.marker.text")
  }

  protected open fun configureContent() {
    myFixture.configureByFile(getTestFileName())
  }

  protected open fun doTest(expectedCount: Int, predicate: (LineMarkerInfo<*>) -> Boolean) {
    configureContent()
    myFixture.doHighlighting()
    val allMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project)
    val markers = allMarkers.filter(predicate)
    assertSize(expectedCount, markers)
    for (info in markers) {
      assertEquals(MarkdownTokenTypes.FENCE_LANG, PsiUtilCore.getElementType(info.element))
    }
  }

  protected fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/lineMarkers/"
  }
}

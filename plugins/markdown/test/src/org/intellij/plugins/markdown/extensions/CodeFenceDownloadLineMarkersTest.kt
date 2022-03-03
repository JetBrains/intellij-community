// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.extensions.common.plantuml.PlantUMLCodeGeneratingProvider
import org.intellij.plugins.markdown.extensions.jcef.mermaid.MermaidBrowserExtension
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
abstract class CodeFenceDownloadLineMarkersTest(createFakeFiles: Boolean): BasePlatformTestCase() {
  private val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val rules: TestRule = RuleChain.outerRule(tempDirectory).around(SetupRule(tempDirectory, createFakeFiles))

  private class SetupRule(private val tempDirectory: TempDirectory, private val createFakeFiles: Boolean): ExternalResource() {
    private var disposable: Disposable? = null

    override fun before() {
      disposable = Disposer.newDisposable()
      ExtensionTestingUtil.mockPathManager(tempDirectory.newDirectoryPath(), disposable!!)
      if (createFakeFiles) {
        val plantUmlExtension = MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<PlantUMLCodeGeneratingProvider>()!!
        val mermaidExtension = MarkdownExtensionsUtil.findBrowserExtensionProvider<MermaidBrowserExtension.Provider>()!!
        ExtensionTestingUtil.createFakeExternalFiles(plantUmlExtension)
        ExtensionTestingUtil.createFakeExternalFiles(mermaidExtension)
      }
    }

    override fun after() {
      disposable?.let(Disposer::dispose)
      disposable = null
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
  fun `mermaid single empty fence`() = doTest(1, ::mermaidPredicate)

  @Test
  fun `mermaid single fence`() = doTest(1, ::mermaidPredicate)

  @Test
  fun `mermaid multiple fences`() = doTest(2, ::mermaidPredicate)

  @Test
  fun `plantuml single fence`() = doTest(1, ::plantumlPredicate)

  @Test
  fun `plantuml multiple fences`() = doTest(3, ::plantumlPredicate)

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

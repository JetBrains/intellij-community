// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.extensions.common.plantuml.PlantUMLCodeGeneratingProvider
import org.intellij.plugins.markdown.extensions.common.plantuml.PlantUMLJarManager
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFenceHtmlCache
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*


@RunWith(JUnit4::class)
class PlantUmlGenerationTest: LightPlatformCodeInsightTestCase() {
  private val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val rules: TestRule = RuleChain.outerRule(tempDirectory).around(SetupRule(tempDirectory))

  class SetupRule(private val tempDirectory: TempDirectory): ExternalResource() {
    private var disposable: Disposable? = null

    override fun before() {
      disposable = Disposer.newDisposable()
      ExtensionTestingUtil.mockPathManager(tempDirectory.newDirectoryPath(), disposable!!)
      val extension = requireNotNull(MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<PlantUMLCodeGeneratingProvider>())
      PlantUMLJarManager.getInstance().dropCache()
      ExtensionTestingUtil.downloadExtension(extension, project = null)
      val state = Collections.singletonMap(extension.id, true)
      ExtensionTestingUtil.replaceExtensionsState(state, disposable!!)
    }

    override fun after() {
      disposable?.let(Disposer::dispose)
      disposable = null
      PlantUMLJarManager.getInstance().dropCache()
    }
  }

  @Test
  fun `test plantUML1`() = doTest()

  @Test
  fun `test plantUML2`() = doTest()

  @Test
  fun `test puml`() = doTest()

  private fun doTest() {
    val virtualFile = getVirtualFile("${getTestName(true)}.md")
    val result = MarkdownUtil.generateMarkdownHtml(virtualFile, VfsUtilCore.loadText(virtualFile), project)
    val hash = MarkdownUtil.md5(virtualFile.path, MarkdownCodeFenceHtmlCache.MARKDOWN_FILE_PATH_KEY)
    println(result)
    assertTrue(result.contains(hash))
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/extensions/plantuml/"
  }
}

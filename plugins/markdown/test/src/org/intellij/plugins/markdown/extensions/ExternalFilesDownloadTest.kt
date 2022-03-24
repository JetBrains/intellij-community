package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExternalFilesDownloadTest: BasePlatformTestCase() {
  private val disposableRule = DisposableRule()
  private val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val rules = RuleChain.outerRule(disposableRule).around(tempDirectory)

  @Test
  fun `test extensions downloadable files can be downloaded`() {
    ExtensionTestingUtil.mockPathManager(tempDirectory.newDirectoryPath(), disposableRule.disposable)
    val extensions = MarkdownExtensionsUtil.collectExtensionsWithExternalFiles().filterIsInstance<MarkdownExtensionWithDownloadableFiles>()
    var hadFails = false
    for (extension in extensions) {
      try {
        ExtensionTestingUtil.downloadExtension(extension, project)
        logger<ExternalFilesDownloadTest>().debug("Successfully downloaded downloadable files for extension: ${extension.id}")
      } catch (exception: Throwable) {
        logger<ExternalFilesDownloadTest>().error(exception)
        hadFails = true
      }
    }
    assertFalse(hadFails)
  }
}

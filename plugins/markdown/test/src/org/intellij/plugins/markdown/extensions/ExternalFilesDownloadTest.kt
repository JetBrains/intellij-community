package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
class ExternalFilesDownloadTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @TempDir
  lateinit var tempDirectory: Path

  @Test
  fun `test extensions downloadable files can be downloaded`() {
    ExtensionTestingUtil.mockPathManager(tempDirectory, disposable)
    val extensions = MarkdownExtensionsUtil.collectExtensionsWithExternalFiles().filterIsInstance<MarkdownExtensionWithDownloadableFiles>()
    var hadFails = false
    for (extension in extensions) {
      try {
        ExtensionTestingUtil.downloadExtension(extension, myFixture.project)
        logger<ExternalFilesDownloadTest>().debug("Successfully downloaded downloadable files for extension: ${extension.id}")
      } catch (exception: Throwable) {
        logger<ExternalFilesDownloadTest>().error(exception)
        hadFails = true
      }
    }
    assertFalse(hadFails)
  }
}

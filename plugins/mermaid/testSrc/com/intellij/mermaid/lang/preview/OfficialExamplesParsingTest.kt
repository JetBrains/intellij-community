package com.intellij.mermaid.lang.preview

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.Path

@TestApplication
class OfficialExamplesParsingTest {
  @JvmField
  @RegisterExtension
  val fixtureExtension = CodeInsightFixtureExtension(OfficialExamplesParsingTest::class.simpleName!!)

  private val fixture: CodeInsightTestFixture
    get() = fixtureExtension.fixture

  private val ignoredTests = listOf<String>()

  @TestTemplate
  @ExtendWith(OfficialDocumentationExamplesContext::class)
  fun testDiagram(file: VirtualFile) {
    Assumptions.assumeFalse { file.nameWithoutExtension in ignoredTests }
    runWriteActionAndWait {
      val localFile = copyFileToProject(file)
      fixture.configureFromExistingVirtualFile(localFile)
    }
    fixture.checkHighlighting(true, true, true, true)
  }

  @RequiresWriteLock
  private fun copyFileToProject(file: VirtualFile): VirtualFile {
    val directory = VfsUtil.findFile(Path(fixture.tempDirPath), true)
    checkNotNull(directory)
    return VfsUtil.copyFile(this, file, directory)
  }
}

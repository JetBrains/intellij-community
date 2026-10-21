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

  /**
   * Upstream documentation examples our grammar does not yet accept, for the mermaid version pinned in
   * gradle.properties. This list is the measured gap and is meant to shrink: when a change fixes a
   * family, delete its entries and the test starts guarding them.
   *
   * Refresh the examples themselves with ./updateMermaidExamples.sh after bumping mermaidVersion.
   */
  private val ignoredTests = listOf(
    // quadrantChart (1) -- `radius: N` alone now parses, but this example also uses the multi-property
    // form (`color: #ff3300, radius: 10`) together with a `:::class` style class. The quadrant body has a
    // single catch-all text token whose character class includes the comma, so separating properties needs
    // that token split up first.
    "quadrantChart-2",
  )

  @TestTemplate
  @ExtendWith(OfficialDocumentationExamplesContext::class)
  fun testDiagram(file: VirtualFile) {
    Assumptions.assumeFalse { file.nameWithoutExtension in ignoredTests }
    runWriteActionAndWait {
      val localFile = copyFileToProject(file)
      fixture.configureFromExistingVirtualFile(localFile)
    }
    // ignoreExtraHighlighting = false: the examples carry no expected-highlighting markup, so anything
    // the editor reports on them is a gap in our grammar. With `true` (as this test used to pass) every
    // unexpected error is ignored and the assertion is vacuous -- a fully red file still passes.
    //
    // checkInfos = false: INFORMATION-level highlights are not gaps. Frontmatter and directives inject
    // YAML and JSON, and every injected fragment reports an INJECTED_FRAGMENT highlight that would
    // otherwise be counted as unexpected.
    fixture.checkHighlighting(true, false, true, false)
  }

  @RequiresWriteLock
  private fun copyFileToProject(file: VirtualFile): VirtualFile {
    val directory = VfsUtil.findFile(Path(fixture.tempDirPath), true)
    checkNotNull(directory)
    return VfsUtil.copyFile(this, file, directory)
  }
}

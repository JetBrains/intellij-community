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
    // block (1)
    "block-28",
    // classDiagram (8)
    "classDiagram-16",
    "classDiagram-17",
    "classDiagram-18",
    "classDiagram-19",
    "classDiagram-21",
    "classDiagram-31",
    "classDiagram-32",
    "classDiagram-33",
    // entityRelationshipDiagram (5)
    "entityRelationshipDiagram-7",
    "entityRelationshipDiagram-12",
    "entityRelationshipDiagram-13",
    "entityRelationshipDiagram-14",
    "entityRelationshipDiagram-15",
    // flowchart (1) -- classDef style values may contain commas (`stroke-dasharray: 9,5`), which the
    // style lexer reads as a declaration separator. Pre-existing, not a v11 gap.
    "flowchart-86",
    // gantt (1)
    "gantt-3",
    // gitgraph (1)
    "gitgraph-19",
    // quadrantChart (1)
    "quadrantChart-2",
    // requirementDiagram (5)
    "requirementDiagram-1",
    "requirementDiagram-3",
    "requirementDiagram-4",
    "requirementDiagram-5",
    "requirementDiagram-6",
    // sequenceDiagram (9)
    "sequenceDiagram-3",
    "sequenceDiagram-4",
    "sequenceDiagram-5",
    "sequenceDiagram-6",
    "sequenceDiagram-7",
    "sequenceDiagram-8",
    "sequenceDiagram-10",
    "sequenceDiagram-11",
    "sequenceDiagram-12",
    // xyChart (3)
    "xyChart-1",
    "xyChart-4",
    "xyChart-5",
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

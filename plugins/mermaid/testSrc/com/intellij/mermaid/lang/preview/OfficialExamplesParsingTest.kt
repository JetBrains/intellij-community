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
    // architecture (3)
    "architecture-0",
    "architecture-1",
    "architecture-2",
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
    // entityRelationshipDiagram (4)
    "entityRelationshipDiagram-11",
    "entityRelationshipDiagram-12",
    "entityRelationshipDiagram-13",
    "entityRelationshipDiagram-14",
    // eventmodeling (12)
    "eventmodeling-0",
    "eventmodeling-1",
    "eventmodeling-2",
    "eventmodeling-3",
    "eventmodeling-4",
    "eventmodeling-5",
    "eventmodeling-6",
    "eventmodeling-7",
    "eventmodeling-8",
    "eventmodeling-9",
    "eventmodeling-10",
    "eventmodeling-11",
    // flowchart (1) -- not a v11 gap. classDef style values may themselves contain commas
    // (`stroke-dasharray: 9,5`), but the lexer treats every comma as a declaration separator and then
    // expects a key, so it reports "STYLE_OPT expected, got '5'". Pre-existing, affects style/classDef
    // generally rather than flowchart specifically.
    "flowchart-86",
    // gantt (1)
    "gantt-3",
    // gitgraph (1)
    "gitgraph-19",
    // ishikawa (1)
    "ishikawa-0",
    // kanban (3)
    "kanban-0",
    "kanban-1",
    "kanban-2",
    // packet (3)
    "packet-0",
    "packet-1",
    "packet-2",
    // quadrantChart (1)
    "quadrantChart-2",
    // radar (3)
    "radar-0",
    "radar-1",
    "radar-2",
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
    // treeView (2)
    "treeView-0",
    "treeView-1",
    // treemap (8)
    "treemap-0",
    "treemap-1",
    "treemap-2",
    "treemap-3",
    "treemap-4",
    "treemap-5",
    "treemap-6",
    "treemap-7",
    // venn (5)
    "venn-0",
    "venn-1",
    "venn-2",
    "venn-3",
    "venn-4",
    // wardley (23)
    "wardley-0",
    "wardley-1",
    "wardley-2",
    "wardley-3",
    "wardley-4",
    "wardley-5",
    "wardley-6",
    "wardley-7",
    "wardley-8",
    "wardley-9",
    "wardley-10",
    "wardley-11",
    "wardley-12",
    "wardley-13",
    "wardley-14",
    "wardley-15",
    "wardley-16",
    "wardley-17",
    "wardley-18",
    "wardley-19",
    "wardley-20",
    "wardley-21",
    "wardley-22",
    // xyChart (1)
    "xyChart-1",
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

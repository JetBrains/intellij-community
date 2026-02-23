package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkFilterContext
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalGenericFileFilter
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalOpenFileHyperlinkInfo
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

internal class TerminalGenericFileFilterRelativePathTest {

  private val localFileSystem: LocalFileSystem = Mockito.mock()
  private val project: Project = Mockito.mock()
  private val filterContext: TerminalHyperlinkFilterContext = Mockito.mock()
  private val eelDescriptor: EelDescriptor = Mockito.mock()
  private val osFamily: EelOsFamily = EelOsFamily.Posix

  // Mock virtual file system structure
  private val rootDir: NewVirtualFile = Mockito.mock()
  private val srcDir: NewVirtualFile = Mockito.mock()
  private val mainKt: NewVirtualFile = Mockito.mock()
  private val testDir: NewVirtualFile = Mockito.mock()
  private val testKt: NewVirtualFile = Mockito.mock()
  private val libDir: NewVirtualFile = Mockito.mock()
  private val utilKt: NewVirtualFile = Mockito.mock()
  private val readmeMd: NewVirtualFile = Mockito.mock()

  private lateinit var filter: TerminalGenericFileFilter

  @Before
  fun setUp() {
    // setup Unix-like system
    whenever(eelDescriptor.osFamily).thenReturn(osFamily)
    whenever(filterContext.eelDescriptor).thenReturn(eelDescriptor)

    setupVirtualFile(rootDir, "/project", true)
    setupVirtualFile(srcDir, "/project/src", true)
    setupVirtualFile(mainKt, "/project/src/Main.kt", false)
    setupVirtualFile(testDir, "/project/test", true)
    setupVirtualFile(testKt, "/project/test/Test.kt", false)
    setupVirtualFile(libDir, "/project/lib", true)
    setupVirtualFile(utilKt, "/project/lib/Util.kt", false)
    setupVirtualFile(readmeMd, "/project/README.md", false)

    setupNewVirtualFileChildren(rootDir, mapOf(
      "src" to srcDir,
      "test" to testDir,
      "lib" to libDir,
      "README.md" to readmeMd
    ))
    setupNewVirtualFileChildren(srcDir, mapOf("Main.kt" to mainKt))
    setupNewVirtualFileChildren(testDir, mapOf("Test.kt" to testKt))
    setupNewVirtualFileChildren(libDir, mapOf("Util.kt" to utilKt))

    whenever(filterContext.currentWorkingDirectory).thenReturn(rootDir)

    filter = TerminalGenericFileFilter(project, filterContext, localFileSystem)
  }

  private fun setupVirtualFile(file: VirtualFile, path: String, isDirectory: Boolean) {
    whenever(file.path).thenReturn(path)
    whenever(file.isValid).thenReturn(true)
    whenever(file.isDirectory).thenReturn(isDirectory)
    whenever(localFileSystem.findFileByPathIfCached(eq(path))).thenReturn(file)
  }

  private fun setupNewVirtualFileChildren(parent: NewVirtualFile, children: Map<String, NewVirtualFile>) {
    children.forEach { (name, child) ->
      whenever(parent.findChildIfCached(eq(name))).thenReturn(child)
      whenever(child.parent).thenReturn(parent)
    }
  }

  private class ExpectedLink(
    val file: VirtualFile,
    val startOffset: Int,
    val endOffset: Int,
    val oneBasedLine: Int = 1,
    val oneBasedColumn: Int = 1,
  )

  private fun applyFilter(line: String, filter: TerminalGenericFileFilter = this.filter): Filter.Result? {
    val lineWithNewline = if (line.endsWith('\n')) line else "$line\n"
    return filter.applyFilter(lineWithNewline, lineWithNewline.length)
  }

  private fun assertEqualLinks(actualLink: Filter.ResultItem, expectedLink: ExpectedLink) {
    Assertions.assertThat(actualLink.highlightStartOffset).isEqualTo(expectedLink.startOffset)
    Assertions.assertThat(actualLink.highlightEndOffset).isEqualTo(expectedLink.endOffset)
    Assertions.assertThat(actualLink.hyperlinkInfo).isInstanceOf(TerminalOpenFileHyperlinkInfo::class.java)
    val hyperlinkInfo = actualLink.hyperlinkInfo as TerminalOpenFileHyperlinkInfo
    Assertions.assertThat(hyperlinkInfo.virtualFile).isEqualTo(expectedLink.file)
    if (expectedLink.file.isFile) {
      Assertions.assertThat(hyperlinkInfo.lineNumber + 1).isEqualTo(expectedLink.oneBasedLine)
      Assertions.assertThat(hyperlinkInfo.columnNumber + 1).isEqualTo(expectedLink.oneBasedColumn)
    }
  }

  private fun assertSingleLink(
    result: Filter.Result?,
    expectedFile: VirtualFile,
    expectedLinkStartOffset: Int,
    expectedLinkEndOffset: Int,
    oneBasedLine: Int = 1,
    oneBasedColumn: Int = 1,
  ) {
    Assertions.assertThat(result).isNotNull
    Assertions.assertThat(result!!.resultItems).hasSize(1)
    val item = result.resultItems[0]
    assertEqualLinks(item, ExpectedLink(expectedFile, expectedLinkStartOffset, expectedLinkEndOffset, oneBasedLine, oneBasedColumn))
  }

  private fun assertLinks(result: Filter.Result?, vararg expectedLinks: ExpectedLink) {
    Assertions.assertThat(result).isNotNull
    val actualLinks = result!!.resultItems.sortedBy { it.highlightStartOffset }
    Assertions.assertThat(actualLinks).hasSize(expectedLinks.size)
    actualLinks.forEachIndexed { index, item ->
      assertEqualLinks(item, expectedLinks[index])
    }
  }

  private fun assertNoLinks(line: String, filter: TerminalGenericFileFilter = this.filter) {
    Assertions.assertThat(applyFilter(line, filter)).isNull()
  }

  @Test
  fun `recognize simple relative path`() {
    val result = applyFilter("src/Main.kt")
    assertSingleLink(result, mainKt, 0, 11)
  }

  @Test
  fun `recognize relative path with line number`() {
    val result = applyFilter("src/Main.kt:42")
    assertSingleLink(result, mainKt, 0, 14, 42)
  }

  @Test
  fun `recognize relative path with line and column numbers`() {
    val result = applyFilter("src/Main.kt:10:5")
    assertSingleLink(result, mainKt, 0, 16, 10, 5)
  }

  @Test
  fun `recognize relative path with parenthesis format`() {
    val result = applyFilter("src/Main.kt: (10, 5): error")
    assertSingleLink(result, mainKt, 0, 20, 10, 5)
  }

  @Test
  fun `recognize relative path in middle of line`() {
    val result = applyFilter("Error in src/Main.kt on line 10")
    assertSingleLink(result, mainKt, 9, 20)
  }

  @Test
  fun `recognize multiple relative paths in one line`() {
    val result = applyFilter("Compare src/Main.kt with test/Test.kt")
    assertLinks(result, ExpectedLink(mainKt, 8, 19), ExpectedLink(testKt, 25, 37))
  }

  @Test
  fun `ignore nonexistent relative paths`() {
    assertNoLinks("src/NonExistent.kt")
  }

  @Test
  fun `ignore paths that exceed FILENAME_MAX per segment`() {
    val longSegment = "a".repeat(TerminalGenericFileFilter.FILENAME_MAX + 1)
    assertNoLinks("src/$longSegment.kt")
  }

  @Test
  fun `recognize file in current directory`() {
    val result = applyFilter("README.md")
    assertSingleLink(result, readmeMd, 0, 9)
  }

  @Test
  fun `recognize nested relative path`() {
    val result = applyFilter("lib/Util.kt:5")
    assertSingleLink(result, utilKt, 0, 13, 5)
  }

  @Test
  fun `handle mixed absolute and relative paths`() {
    val result = applyFilter("/project/src/Main.kt and src/Main.kt")
    assertLinks(result, ExpectedLink(mainKt, 0, 20), ExpectedLink(mainKt, 25, 36))
  }

  @Test
  fun `ignore tilde paths as relative paths`() {
    val result = applyFilter("~/src/Main.kt")
    Assertions.assertThat(result).isNull()
  }

  @Test
  fun `handle paths with special characters before link`() {
    val result = applyFilter("File: src/Main.kt")
    assertSingleLink(result, mainKt, 6, 17)
  }

  @Test
  fun `handle paths with colon separator at end`() {
    val result = applyFilter("src/Main.kt:")
    assertSingleLink(result, mainKt, 0, 11)
  }

  @Test
  fun `handle paths with dot slash prefix`() {
    val result = applyFilter("Written to ./src/Main.kt:10:2, took 5ms")
    assertSingleLink(result, mainKt, 11, 29, 10, 2)
  }

  @Test
  fun `handle paths inside brackets`() {
    val result = applyFilter("[./src/Main.kt]")
    assertSingleLink(result, mainKt, 1, 14)
  }

  @Test
  fun `recognize paths with two dots slash prefix`() {
    whenever(filterContext.currentWorkingDirectory).thenReturn(srcDir)
    val result = applyFilter("../test/Test.kt")
    assertSingleLink(result, testKt, 0, 15)

    assertNoLinks("")
    assertNoLinks(".")
    assertNoLinks("..")
    assertNoLinks("...")
    assertNoLinks("cd .., stop")
  }

  @Test
  fun `recognize mixed paths with dots slash prefixes`() {
    whenever(filterContext.currentWorkingDirectory).thenReturn(srcDir)
    val result = applyFilter("test ./Main.kt -> ../test/Test.kt")
    assertLinks(result, ExpectedLink(mainKt, 5, 14), ExpectedLink(testKt, 18, 33))
  }

  @Test
  fun `no context means no relative paths`() {
    assertNoLinks("src/Main.kt", TerminalGenericFileFilter(project, null, localFileSystem))
  }

  @Test
  fun `no current working directory means no relative paths`() {
    whenever(filterContext.currentWorkingDirectory).thenReturn(null)
    assertNoLinks("src/Main.kt")
  }

  @Test
  fun `real world gradle error with relative paths`() {
    val output = """
      > Task :app:compileKotlin FAILED
      e: src/Main.kt:10:5: Unresolved reference: foo
      e: src/Main.kt:15:12: Type mismatch

      FAILURE: Build failed with an exception.
    """.trimIndent()

    var totalLength = 0
    val results = output.lines().mapNotNull { line ->
      val lineWithNewline = "$line\n"
      totalLength += lineWithNewline.length
      filter.applyFilter(lineWithNewline, totalLength)
    }

    val allLinks = results.flatMap { it.resultItems }
    Assertions.assertThat(allLinks.size).isEqualTo(2)
    assertEqualLinks(allLinks[0], ExpectedLink(mainKt, 36, 52, 10, 5))
    assertEqualLinks(allLinks[1], ExpectedLink(mainKt, 83, 100, 15, 12))
  }

  @Test
  fun `real world test output with relative paths`() {
    val result = applyFilter("AssertionError at test/Test.kt:25")
    assertSingleLink(result, testKt, 18, 33, 25)
  }

  @Test
  fun `backticks around relative paths`() {
    val result = applyFilter("See `src\\Main.kt`, `test\\Test.kt:10`  and `.\\lib\\Util.kt:24:10`")
    assertLinks(result, ExpectedLink(mainKt, 5, 16), ExpectedLink(testKt, 20, 35, 10), ExpectedLink(utilKt, 43, 62, 24, 10))
  }

  @Test
  fun `parenthesis around relative paths`() {
    val result = applyFilter("● Update(src\\Main.kt)")
    assertLinks(result, ExpectedLink(mainKt, 9, 20))
  }

  @Test
  fun `parenthesis around several relative paths`() {
    val result = applyFilter("Update(src\\Main.kt, test\\Test.kt:10:2), delete(lib\\Util.kt)")
    assertLinks(result, ExpectedLink(mainKt, 7, 18), ExpectedLink(testKt, 20, 37, 10, 2), ExpectedLink(utilKt, 47, 58))
  }

  @Test
  fun `file paths in --option=path`() {
    val result = applyFilter("tar --create --file=./lib/Util.kt --directory=src  src/Main.kt")
    assertLinks(result, ExpectedLink(utilKt, 20, 33), ExpectedLink(srcDir, 46, 49), ExpectedLink(mainKt, 51, 62))
  }

  @Test
  fun `directory path with trailing slash`() {
    val result = applyFilter("cp -r ./lib/ src/")
    assertLinks(result, ExpectedLink(libDir, 6, 12), ExpectedLink(srcDir, 13, 17))
  }
}

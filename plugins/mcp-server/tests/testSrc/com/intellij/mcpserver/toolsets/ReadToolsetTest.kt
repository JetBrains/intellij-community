@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ReadToolset
import com.intellij.mcpserver.toolsets.general.SearchToolset
import com.intellij.mcpserver.util.attachJarLibrary
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.mcpserver.util.findJarLibraryEntry
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class ReadToolsetTest : GeneralMcpToolsetTestBase() {
  companion object {
    private const val CSV_FORMAT_CLASS_PATH = "org/apache/commons/csv/CSVFormat.class"
  }

  private val json = Json { ignoreUnknownKeys = true }
  private val readFileFixture = sourceRootFixture.virtualFileFixture(
    "read_file_sample.txt",
    """
      // Header A
      // Header B
      fun foo() {
        val x = 1
        if (x > 0) {
          println(x)
        }
      }
      fun bar() {
        println("bar")
      }
    """.trimIndent()
  )
  private val readFile by readFileFixture
  private val commonsCsvJar by projectFixture.fileOrDirInProjectFixture("libraries/commons-csv/commons-csv-1.14.1.jar")

  @Serializable
  private data class SearchItem(val filePath: String)

  @Serializable
  private data class SearchResult(val items: List<SearchItem> = emptyList())

  @Test
  fun read_file_slice_returns_numbered_lines() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(3))
        put("max_lines", JsonPrimitive(2))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L3: fun foo() {", "L4:   val x = 1")
    }
  }

  @Test
  fun read_file_slice_rejects_offset_beyond_file_length() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(999))
        put("max_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("start_line exceeds file length")
    }
  }

  @Test
  fun read_file_slice_rejects_non_positive_limit() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(1))
        put("max_lines", JsonPrimitive(0))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("max_lines must be > 0")
    }
  }

  @Test
  fun read_file_range_by_lines_is_inclusive() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("lines"))
        put("start_line", JsonPrimitive(2))
        put("end_line", JsonPrimitive(4))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L2: // Header B",
        "L3: fun foo() {",
        "L4:   val x = 1",
      )
      assertThat(text).doesNotContain("L5:   if (x > 0) {")
    }
  }

  @Test
  fun read_file_indentation_includes_header_lines() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("max_lines", JsonPrimitive(3))
        put("start_line", JsonPrimitive(3))
        put("include_header", JsonPrimitive(true))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L1: // Header A",
        "L2: // Header B",
        "L3: fun foo() {",
      )
    }
  }

  @Test
  fun read_file_indentation_respects_max_lines() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("start_line", JsonPrimitive(3))
        put("max_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).isEqualTo("L3: fun foo() {")
    }
  }

  @Test
  fun read_file_indentation_excludes_siblings_when_disabled() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("max_lines", JsonPrimitive(20))
        put("start_line", JsonPrimitive(3))
        put("include_siblings", JsonPrimitive(false))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L3: fun foo() {")
      assertThat(text).doesNotContain("fun bar() {")
    }
  }

  @Test
  fun read_file_range_by_line_column_includes_context_lines() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("line_columns"))
        put("start_line", JsonPrimitive(4))
        put("start_column", JsonPrimitive(3))
        put("end_line", JsonPrimitive(4))
        put("end_column", JsonPrimitive(6))
        put("context_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L3: fun foo() {",
        "L4:   val x = 1",
        "L5:   if (x > 0) {",
      )
    }
  }

  @Test
  fun read_file_range_by_offsets_reads_exact_line() = runBlocking(Dispatchers.Default) {
    val fileText = readFile.contentsToByteArray().toString(Charsets.UTF_8)
    val needle = "println(\"bar\")"
    val startOffset = fileText.indexOf(needle)
    val endOffset = startOffset + needle.length
    assertThat(startOffset).isGreaterThanOrEqualTo(0)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("offsets"))
        put("start_offset", JsonPrimitive(startOffset))
        put("end_offset", JsonPrimitive(endOffset))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L10:   println(\"bar\")")
    }
  }

  @Test
  fun read_file_supports_relative_anchor_path_for_library_class() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val anchorPath = searchExternalSymbolPath("CSVFormat")
    assertThat(anchorPath).contains("CSVFormat.class")

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(anchorPath))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }
  }

  @Test
  fun read_file_supports_absolute_path_and_jar_url_for_library_class() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val classFile = findJarLibraryEntry(commonsCsvJar, CSV_FORMAT_CLASS_PATH)
    val anchorPath = searchExternalSymbolPath("CSVFormat")
    assertThat(anchorPath).contains("CSVFormat.class")

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(classFile.path))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(classFile.url))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }
  }

  @Test
  fun read_file_reads_binary_library_class_without_opening_editor() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val classFile = findJarLibraryEntry(commonsCsvJar, CSV_FORMAT_CLASS_PATH)
    val fileOpened = AtomicBoolean(false)
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
      override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        fileOpened.set(true)
      }
    })
    try {
      testMcpTool(
        ReadToolset::read_file.name,
        buildJsonObject {
          put("file_path", JsonPrimitive(classFile.url))
        }
      ) { actualResult ->
        assertThat(actualResult.textContent.text).contains("class CSVFormat")
        assertThat(fileOpened.get()).isFalse()
      }
    }
    finally {
      connection.disconnect()
    }
  }

  @Test
  fun read_file_supports_jar_url_for_library_class() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val classFile = findJarLibraryEntry(commonsCsvJar, CSV_FORMAT_CLASS_PATH)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(classFile.url))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }
  }

  @Test
  fun read_file_supports_raw_jar_entry_path() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val classFile = findJarLibraryEntry(commonsCsvJar, CSV_FORMAT_CLASS_PATH)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(classFile.path))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }
  }

  @Test
  fun read_file_rejects_unrelated_external_file() = runBlocking(Dispatchers.Default) {
    val externalFile = Files.createTempFile("mcp_read_file_external", ".txt")
    Files.writeString(externalFile, "external file content")

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(externalFile.toString()))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("outside project, library, and SDK roots")
    }
  }

  @Test
  fun read_file_reads_excluded_file_in_project_directory() = runBlocking(Dispatchers.Default) {
    val excludedDir = project.projectDirectory.resolve("src/excluded")
    Files.createDirectories(excludedDir)
    val excludedFilePath = excludedDir.resolve("excluded.txt")
    Files.writeString(excludedFilePath, "excluded project file")
    val excludedDirVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(excludedDir)
                           ?: error("Cannot resolve $excludedDir")

    edtWriteAction {
      ModuleRootModificationUtil.updateModel(moduleFixture.get()) { model ->
        model.contentEntries.single().addExcludeFolder(excludedDirVFile)
      }
    }
    awaitExternalChangesAndIndexing(project)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativize(excludedFilePath).toString()))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("excluded project file")
    }
  }

  @Test
  fun read_file_reads_file_in_project_directory_outside_content_root() = runBlocking(Dispatchers.Default) {
    val projectFilePath = project.projectDirectory.resolve("project-only.txt")
    Files.writeString(projectFilePath, "project directory file")

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive("project-only.txt"))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("project directory file")
    }
  }

  @Test
  fun read_file_reads_file_in_external_content_root() = runBlocking(Dispatchers.Default) {
    val externalRoot = Files.createTempDirectory("mcp_external_content_root")
    val externalFilePath = externalRoot.resolve("external-content.txt")
    Files.writeString(externalFilePath, "external content root file")

    edtWriteAction {
      ModuleRootModificationUtil.addContentRoot(moduleFixture.get(), externalRoot.toString())
    }
    awaitExternalChangesAndIndexing(project)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(externalFilePath.toString()))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("external content root file")
    }
  }

  @Test
  fun read_file_reads_anchor_path_from_search_symbol_result() = runBlocking(Dispatchers.Default) {
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")
    val anchorPath = searchExternalSymbolPath("CSVFormat")
    assertThat(anchorPath).contains("CSVFormat.class")

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(anchorPath))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).contains("class CSVFormat")
    }
  }

  private suspend fun searchExternalSymbolPath(symbolName: String): String {
    var resultPath: String? = null
    withConnection { client ->
      val actualResult = client.callTool(
        SearchToolset::search_symbol.name,
        buildJsonObject {
          put("q", JsonPrimitive(symbolName))
          put("include_external", JsonPrimitive(true))
        },
        options = RequestOptions(timeout = 180.seconds),
      )
      val payload = json.decodeFromString(SearchResult.serializer(), actualResult.textContent.text)
      resultPath = payload.items.firstOrNull { it.filePath.contains("$symbolName.class") }?.filePath
    }
    return requireNotNull(resultPath) { "Cannot find external symbol $symbolName" }
  }

}

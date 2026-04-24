@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.SearchToolset
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class SearchToolsetTest : McpToolsetTestBase() {
  private val json = Json { ignoreUnknownKeys = true }

  private val searchFile by sourceRootFixture.virtualFileFixture(
    "se_unique_search_file_7c2f.txt",
    "Search Everywhere file content"
  )

  private val fileMaskTxt by sourceRootFixture.virtualFileFixture(
    "se_mask_match_4d9a.txt",
    "Search Everywhere file mask content"
  )

  private val fileMaskJava by sourceRootFixture.virtualFileFixture(
    "se_mask_match_4d9a.java",
    "Search Everywhere file mask content"
  )

  private val subdir1Fixture = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path("subdir1")))
  private val subdir2Fixture = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path("subdir2")))
  private val scopedFileInSubdir1 by subdir1Fixture.virtualFileFixture(
    "se_scoped_file_3e7a.txt",
    "Scoped file content"
  )
  private val scopedJavaFileInSubdir1 by subdir1Fixture.virtualFileFixture(
    "se_scoped_java_3e7a.java",
    "Scoped file content"
  )
  private val scopedFileInSubdir2 by subdir2Fixture.virtualFileFixture(
    "se_scoped_file_3e7a.txt",
    "Scoped file content"
  )

  private val symbolPrefix = "SeSymbol9e1b"
  private val symbolFileInSubdir1 by subdir1Fixture.virtualFileFixture(
    "se_symbol_9e1b_sub1.kt",
    "class ${symbolPrefix}Alpha {}\n"
  )
  private val symbolFileInSubdir2 by subdir2Fixture.virtualFileFixture(
    "se_symbol_9e1b_sub2.kt",
    "class ${symbolPrefix}Beta {}\n"
  )

  private val maxResultsFile1 by sourceRootFixture.virtualFileFixture(
    "se_max_results_8b1c_1.txt",
    "Max results content"
  )

  private val maxResultsFile2 by sourceRootFixture.virtualFileFixture(
    "se_max_results_8b1c_2.txt",
    "Max results content"
  )

  @Serializable
  private data class SearchItem(
    val filePath: String,
    val startLine: Int? = null,
    val startColumn: Int? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val lineText: String? = null,
  )

  @Serializable
  private data class SearchResult(
    val items: List<SearchItem> = emptyList(),
    val more: Boolean = false,
  )

  private val excludedDirName = "se_excluded_dir_51a2"
  private val excludedFileName = "se_excluded_file_51a2.txt"
  private val excludedFileContent = "Excluded file content"

  private val pathExcludedDirName = "se_paths_excluded_dir_9f3b"
  private val pathExcludedFileName = "se_paths_excluded_file_9f3b.txt"

  private fun parseResult(text: String?): SearchResult {
    val payload = text ?: error("Tool call result should include text content")
    return json.decodeFromString(SearchResult.serializer(), payload)
  }

  private fun SearchResult.filePaths(): List<String> = items.map { it.filePath }

  private suspend fun createExcludedFile(): VirtualFile = edtWriteAction {
    val rootDir = sourceRootFixture.get().virtualFile
    val excludedDir = rootDir.findChild(excludedDirName) ?: rootDir.createChildDirectory(this, excludedDirName)
    val excludedFile = excludedDir.findChild(excludedFileName) ?: excludedDir.createChildData(this, excludedFileName).also {
      it.setBinaryContent(excludedFileContent.toByteArray())
    }
    ModuleRootModificationUtil.updateExcludedFolders(
      moduleFixture.get(),
      rootDir,
      emptyList(),
      listOf(excludedDir.url)
    )
    excludedFile
  }

  private suspend fun createFileInSubdir1(directoryName: String, fileName: String, content: String): VirtualFile = edtWriteAction {
    val rootDir = subdir1Fixture.get().virtualFile
    val childDir = rootDir.findChild(directoryName) ?: rootDir.createChildDirectory(this, directoryName)
    val file = childDir.findChild(fileName) ?: childDir.createChildData(this, fileName).also {
      it.setBinaryContent(content.toByteArray())
    }
    file
  }

  @Test
  fun search_file_returns_file_path() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = searchFile.name
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(fileName))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val item = result.items.firstOrNull { it.filePath.contains(fileName) }
      assertThat(item).isNotNull
      assertThat(item?.startLine).isNull()
      assertThat(item?.startColumn).isNull()
      assertThat(item?.endLine).isNull()
      assertThat(item?.endColumn).isNull()
      assertThat(item?.startOffset).isNull()
      assertThat(item?.endOffset).isNull()
      assertThat(item?.lineText).isNull()
    }
  }

  @Test
  fun search_file_supports_explicit_globstar_prefix() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = searchFile.name
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive("**/$fileName"))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      assertThat(result.items).anyMatch { it.filePath.contains(fileName) }
    }
  }

  @Test
  fun search_file_supports_exact_subpath_queries() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = scopedFileInSubdir1.name
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive("subdir1/$fileName"))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).containsExactly("subdir1/$fileName")
    }
  }

  @Test
  fun search_file_respects_paths_scope() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = scopedFileInSubdir1.name
    val otherFileName = scopedFileInSubdir2.name
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(fileName))
        put("paths", JsonArray(listOf(JsonPrimitive("subdir1/**"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains("subdir1") }
      assertThat(filePaths).noneMatch { it.contains("subdir2") }
      assertThat(otherFileName).isEqualTo(fileName)
    }
  }

  @Test
  fun search_file_respects_paths_excludes() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = scopedFileInSubdir1.name
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(fileName))
        put("paths", JsonArray(listOf(
          JsonPrimitive("subdir1/**"),
          JsonPrimitive("!$fileName"),
        )))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      assertThat(result.items).isEmpty()
      assertThat(result.more).isFalse()
    }
  }

  @Test
  fun search_file_includes_excluded_files_when_requested() = runBlocking {
    val excludedFile = createExcludedFile()
    DumbService.getInstance(project).waitForSmartMode()
    val fileName = excludedFile.name

    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(fileName))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).noneMatch { it.contains(fileName) }
    }

    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(fileName))
        put("includeExcluded", JsonPrimitive(true))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(fileName) }
    }
  }

  @Test
  fun search_text_returns_snippet_details() = runBlocking {
    awaitExternalChangesAndIndexing(project)
    val query = "Search Everywhere file content"
    testMcpTool(
      SearchToolset::search_text.name,
      buildJsonObject {
        put("q", JsonPrimitive(query))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val item = result.items.firstOrNull { it.filePath.contains(searchFile.name) }
      assertThat(item).isNotNull
      assertThat(item?.startLine).isNotNull
      assertThat(item?.startColumn).isNotNull
      assertThat(item?.endLine).isNotNull
      assertThat(item?.endColumn).isNotNull
      assertThat(item?.startOffset).isNotNull
      assertThat(item?.endOffset).isNotNull
      assertThat(item?.lineText).contains("||")
      assertThat(item?.lineText).contains("Search Everywhere")
    }
  }

  @Test
  fun search_text_returns_match_coordinates() = runBlocking {
    awaitExternalChangesAndIndexing(project)
    val query = "Search Everywhere file content"
    testMcpTool(
      SearchToolset::search_text.name,
      buildJsonObject {
        put("q", JsonPrimitive(query))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val item = result.items.firstOrNull { it.filePath.contains(searchFile.name) }
      assertThat(item).isNotNull
      assertThat(item?.startLine).isEqualTo(1)
      assertThat(item?.endLine).isEqualTo(1)
      assertThat(item?.startColumn).isEqualTo(1)
      assertThat(item?.startOffset).isEqualTo(0)
      assertThat(item?.endOffset).isEqualTo(query.length)
      assertThat(item?.endColumn).isEqualTo(query.length + 1)
    }
  }

  @Test
  fun search_regex_returns_match_coordinates() = runBlocking {
    awaitExternalChangesAndIndexing(project)
    testMcpTool(
      SearchToolset::search_regex.name,
      buildJsonObject {
        put("q", JsonPrimitive("Search\\s+Everywhere"))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val item = result.items.firstOrNull { it.filePath.contains(searchFile.name) }
      assertThat(item).isNotNull
      assertThat(item?.startLine).isNotNull
      assertThat(item?.endLine).isNotNull
      assertThat(item?.startColumn).isNotNull
      assertThat(item?.endColumn).isNotNull
      assertThat(item?.startOffset).isNotNull
      assertThat(item?.endOffset).isNotNull
    }
  }

  @Test
  fun search_text_respects_paths_glob() = runBlocking {
    awaitExternalChangesAndIndexing(project)
    val query = "Search Everywhere file mask content"
    testMcpTool(
      SearchToolset::search_text.name,
      buildJsonObject {
        put("q", JsonPrimitive(query))
        put("paths", JsonArray(listOf(JsonPrimitive("**/*.txt"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(fileMaskTxt.name) }
      assertThat(filePaths).noneMatch { it.contains(fileMaskJava.name) }
    }
  }

  @Test
  fun search_text_respects_paths_and_exclude() = runBlocking {
    awaitExternalChangesAndIndexing(project)

    testMcpTool(
      SearchToolset::search_text.name,
      buildJsonObject {
        put("q", JsonPrimitive("Scoped file content"))
        put("paths", JsonArray(listOf(
          JsonPrimitive("subdir1/**"),
          JsonPrimitive("!**/*.java")
        )))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(scopedFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains(scopedJavaFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains("subdir2") }
    }
  }

  @Test
  fun search_text_respects_directory_excludes() = runBlocking {
    val excludedByPathsFile = createFileInSubdir1(pathExcludedDirName, pathExcludedFileName, "Scoped file content")
    DumbService.getInstance(project).waitForSmartMode()
    testMcpTool(
      SearchToolset::search_text.name,
      buildJsonObject {
        put("q", JsonPrimitive("Scoped file content"))
        put("paths", JsonArray(listOf(
          JsonPrimitive("subdir1/**"),
          JsonPrimitive("!subdir1/$pathExcludedDirName/**"),
          JsonPrimitive("!**/*.java"),
        )))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(scopedFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains(scopedJavaFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains(excludedByPathsFile.name) }
      assertThat(filePaths).noneMatch { it.contains("subdir2") }
    }
  }

  @Test
  fun search_regex_respects_paths_scope() = runBlocking {
    awaitExternalChangesAndIndexing(project)
    testMcpTool(
      SearchToolset::search_regex.name,
      buildJsonObject {
        put("q", JsonPrimitive("Scoped\\s+file\\s+content"))
        put("paths", JsonArray(listOf(JsonPrimitive("subdir1/**"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(scopedFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains("subdir2") }
    }
  }

  @Test
  fun search_symbol_returns_snippet_details() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val query = "${symbolPrefix}Alpha"
    testMcpTool(
      SearchToolset::search_symbol.name,
      buildJsonObject {
        put("q", JsonPrimitive(query))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val item = result.items.firstOrNull { it.filePath.contains(symbolFileInSubdir1.name) }
      assertThat(item).isNotNull
      assertThat(item?.startLine).isNotNull
      assertThat(item?.lineText).contains(query)
    }
  }

  @Test
  fun search_symbol_accepts_directory_path_without_trailing_slash() = runBlocking {
    val directoryName = "se_symbol_dir_4a1c"
    val fileName = "se_symbol_dir_4a1c.kt"
    val symbolName = "SeSymbolDir4a1c"
    createFileInSubdir1(directoryName, fileName, "class $symbolName {}\n")
    DumbService.getInstance(project).waitForSmartMode()

    testMcpTool(
      SearchToolset::search_symbol.name,
      buildJsonObject {
        put("q", JsonPrimitive(symbolName))
        put("paths", JsonArray(listOf(JsonPrimitive("subdir1/$directoryName"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains("subdir1/$directoryName/$fileName") }
    }
  }

  @Test
  fun search_symbol_respects_paths_scope() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    testMcpTool(
      SearchToolset::search_symbol.name,
      buildJsonObject {
        put("q", JsonPrimitive(symbolPrefix))
        put("paths", JsonArray(listOf(JsonPrimitive("subdir1/**"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(symbolFileInSubdir1.name) }
      assertThat(filePaths).noneMatch { it.contains(symbolFileInSubdir2.name) }
    }
  }

  @Test
  fun search_symbol_respects_paths_excludes() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    testMcpTool(
      SearchToolset::search_symbol.name,
      buildJsonObject {
        put("q", JsonPrimitive(symbolPrefix))
        put("paths", JsonArray(listOf(JsonPrimitive("!subdir1/**"))))
      }
    ) { actualResult ->
      val filePaths = parseResult(actualResult.textContent.text).filePaths()
      assertThat(filePaths).anyMatch { it.contains(symbolFileInSubdir2.name) }
      assertThat(filePaths).noneMatch { it.contains(symbolFileInSubdir1.name) }
    }
  }

  @Test
  fun search_symbol_respects_limit_and_sets_more() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    testMcpTool(
      SearchToolset::search_symbol.name,
      buildJsonObject {
        put("q", JsonPrimitive(symbolPrefix))
        put("limit", JsonPrimitive(1))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      assertThat(result.items).hasSize(1)
      assertThat(result.more).isTrue()
      val filePaths = result.filePaths()
      assertThat(filePaths).anyMatch { path ->
        path.contains(symbolFileInSubdir1.name) || path.contains(symbolFileInSubdir2.name)
      }
    }
  }

  @Test
  fun search_file_respects_limit_and_sets_more() = runBlocking {
    DumbService.getInstance(project).waitForSmartMode()
    val query = "se_max_results_8b1c_*.txt"
    testMcpTool(
      SearchToolset::search_file.name,
      buildJsonObject {
        put("q", JsonPrimitive(query))
        put("limit", JsonPrimitive(1))
      }
    ) { actualResult ->
      val result = parseResult(actualResult.textContent.text)
      val entryFilePaths = result.filePaths()
      val expectedNames = setOf(maxResultsFile1.name, maxResultsFile2.name)
      assertThat(result.items).hasSize(1)
      assertThat(result.more).isTrue()
      assertThat(entryFilePaths).anyMatch { path -> expectedNames.any { path.contains(it) } }
    }
  }
}

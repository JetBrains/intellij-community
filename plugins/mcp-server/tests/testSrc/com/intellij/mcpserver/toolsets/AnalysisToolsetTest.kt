@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.AnalysisToolset
import com.intellij.mcpserver.toolsets.general.RequestedLintFile
import com.intellij.mcpserver.toolsets.general.prepareLintFiles
import com.intellij.mcpserver.toolsets.general.prepareRequestedLintFiles
import com.intellij.mcpserver.toolsets.general.withLintFilesCollectorOverride
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AnalysisToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun lint_files() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::lint_files.name,
      buildJsonObject {
        put("file_paths", buildJsonArray {
          add(JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
          add(JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
          add(JsonPrimitive(project.projectDirectory.relativizeIfPossible(testJavaFile)))
        })
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(text).contains(""""items":[{"""")
      assertThat(text).containsOnlyOnce(""""filePath":"src/Main.java"""")
      assertThat(text).contains(""""filePath":"src/Test.java"""")
      assertThat(text).contains(""""problems":[{"""")
    }
  }

  @Test
  fun lint_files_timeout() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::lint_files.name,
      buildJsonObject {
        put("file_paths", buildJsonArray {
          add(JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
        })
        put("timeout", JsonPrimitive(0))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(text).contains(""""items":[]""")
      assertThat(text).contains(""""more":true""")
    }
  }

  @Test
  fun get_file_problems() = runBlocking(Dispatchers.Default) {
    // This test requires Java plugin to detect syntax errors in Java files
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::get_file_problems.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
        put("errorsOnly", JsonPrimitive(false))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(text).contains(""""filePath":"src/Main.java"""")
      assertThat(text).contains(""""errors":[{"""")
    }
  }

  @Test
  fun lint_files_omits_clean_files() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(lintFileResultWithProblem(mainPath))
        onFileResult(AnalysisToolset.LintFileResult(filePath = classPath))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("file_paths", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).doesNotContain(""""more":true""")
        assertThat(text).containsOnlyOnce(""""filePath":"src/Main.java"""")
        assertThat(text).doesNotContain(""""filePath":"src/Class.java"""")
      }
    }
  }

  @Test
  fun lint_files_returns_empty_items_when_all_files_are_clean() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(AnalysisToolset.LintFileResult(filePath = mainPath))
        onFileResult(AnalysisToolset.LintFileResult(filePath = classPath))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("file_paths", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).contains(""""items":[]""")
        assertThat(text).doesNotContain(""""more":true""")
      }
    }
  }

  @Test
  fun get_file_problems_returns_empty_errors_when_file_is_clean() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(AnalysisToolset.LintFileResult(filePath = mainPath))
      },
    ) {
      testMcpTool(
        AnalysisToolset::get_file_problems.name,
        buildJsonObject {
          put("filePath", JsonPrimitive(mainPath))
          put("errorsOnly", JsonPrimitive(false))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).contains(""""filePath":"src/Main.java"""")
        assertThat(text).contains(""""errors":[]""")
        assertThat(text).doesNotContain(""""timedOut":true""")
      }
    }
  }

  @Test
  fun lint_files_returns_partial_results_in_request_order_on_timeout() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)
    val testPath = project.projectDirectory.relativizeIfPossible(testJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(lintFileResultWithProblem(classPath))
        onFileResult(lintFileResultWithProblem(mainPath))
        awaitCancellation()
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("file_paths", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
            add(JsonPrimitive(testPath))
          })
          put("timeout", JsonPrimitive(100))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).contains(""""more":true""")
        assertThat(text).contains(""""filePath":"src/Main.java"""")
        assertThat(text).contains(""""filePath":"src/Class.java"""")
        assertThat(text).doesNotContain(""""filePath":"src/Test.java"""")
        assertThat(text.indexOf(""""filePath":"src/Main.java"""")).isLessThan(text.indexOf(""""filePath":"src/Class.java"""))
      }
    }
  }

  @Test
  fun get_file_problems_timeout() = runBlocking(Dispatchers.Default) {
    val relativePath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    testMcpTool(
      AnalysisToolset::get_file_problems.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(relativePath))
        put("timeout", JsonPrimitive(0))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(text).contains(""""filePath":"src/Main.java"""")
      assertThat(text).contains(""""errors":[]""")
      assertThat(text).contains(""""timedOut":true""")
    }
  }

  @Test
  fun prepareLintFiles_handles_cached_and_uncached_files(): Unit = runBlocking(Dispatchers.Default) {
    val localFileSystem = LocalFileSystem.getInstance()
    val cachedPath = mainJavaFile.toNioPath()
    requireNotNull(localFileSystem.refreshAndFindFileByNioFile(cachedPath))
    val tempDir = Files.createTempDirectory("mcpserver-prepareLintFiles-")
    try {
      val uncachedPath = Files.writeString(tempDir.resolve("uncached.txt"), "uncached")

      val resolvedFiles = prepareLintFiles(
        listOf(
          RequestedLintFile("src/Main.java", "src/Main.java", cachedPath),
          RequestedLintFile("uncached.txt", "uncached.txt", uncachedPath),
        ),
      )

      assertThat(resolvedFiles.map { it.relativePath }).containsExactly("src/Main.java", "uncached.txt")
      assertThat(resolvedFiles[0].virtualFile.toNioPath()).isEqualTo(cachedPath)
      assertThat(resolvedFiles[1].virtualFile.toNioPath()).isEqualTo(uncachedPath)
    }
    finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun prepareRequestedLintFiles_deduplicates_normalized_paths_preserving_first_order(): Unit = runBlocking(Dispatchers.Default) {
    val requestedFiles = prepareRequestedLintFiles(
      project,
      listOf("src/../src/Main.java", "src/Main.java", "./src/Test.java"),
    )

    assertThat(requestedFiles.map { it.relativePath }).containsExactly("src/Main.java", "src/Test.java")
    assertThat(requestedFiles.map { it.requestedPath }).containsExactly("src/../src/Main.java", "./src/Test.java")
  }

  // tool is disabled now
  //@Test
  @Suppress("unused")
  fun build_project() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::build_project.name,
      buildJsonObject {},
      /*language=JSON*/ """{"isSuccess":true,"problems":[]}"""
    )
  }

  @Test
  fun get_project_modules() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::get_project_modules.name,
      buildJsonObject {},
      /*language=JSON*/ """{"modules":[{"name":"testModule","type":""}]}"""
    )
  }

  @Test
  fun get_project_dependencies() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::get_project_dependencies.name,
      buildJsonObject {},
      """{"dependencies":[]}"""
    )
  }

  // TODO handle it better
  /**
   * Checks if the Java plugin is installed.
   * Use this to skip tests that require Java language support.
   */
  private fun isJavaPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.java"))
  }

  private suspend fun withLintFilesCollector(
    collector: suspend (filePaths: List<String>, onFileResult: (AnalysisToolset.LintFileResult) -> Unit) -> Unit,
    action: suspend () -> Unit,
  ) {
    withLintFilesCollectorOverride(
      project,
      collector = { request, onFileResult ->
        collector(request.filePaths, onFileResult)
      },
    ) {
      action()
    }
  }

  private fun lintFileResultWithProblem(filePath: String): AnalysisToolset.LintFileResult {
    return AnalysisToolset.LintFileResult(
      filePath = filePath,
      problems = listOf(
        AnalysisToolset.LintProblem(
          severity = "ERROR",
          description = "Problem",
          lineText = "line",
          line = 1,
          column = 1,
        ),
      ),
    )
  }
}

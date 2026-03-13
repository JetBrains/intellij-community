@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.vcs.VcsToolset
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import io.kotest.common.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class VcsToolsetTest : McpToolsetTestBase() {
  @Test
  fun git_status_returns_empty_when_no_git_roots() = runBlocking {
    cleanupExistingGitRepository(project.projectDirectory)

    testMcpTool(
      VcsToolset::git_status.name,
      buildJsonObject {},
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.repositories()).isEmpty()
    }
  }

  @Test
  fun git_status_returns_structured_status_for_repo() = runBlocking {
    val repositoryPath = project.projectDirectory
    cleanupExistingGitRepository(repositoryPath)
    setupGitRepository(repositoryPath)
    createCommittedBaseline(repositoryPath)

    repositoryPath.resolve("tracked.txt").writeText("changed\n")
    repositoryPath.resolve("staged.txt").writeText("staged\n")
    runGit(repositoryPath, "add", "staged.txt")
    repositoryPath.resolve("untracked.txt").writeText("untracked\n")

    testMcpTool(
      VcsToolset::git_status.name,
      buildJsonObject {
        put("includeUntracked", JsonPrimitive(true))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      val repositories = response.repositories()
      assertThat(repositories).hasSize(1)

      val repository = repositories.first().jsonObject
      assertThat(repository.bool("isClean")).isFalse()
      assertThat(repository.int("totalEntries")).isEqualTo(3)
      assertThat(repository.bool("hasMoreEntries")).isFalse()
      assertThat(repository.int("stagedCount")).isEqualTo(1)
      assertThat(repository.int("unstagedCount")).isEqualTo(1)
      assertThat(repository.int("untrackedCount")).isEqualTo(1)
      assertThat(repository.int("ignoredCount")).isZero()
      assertThat(repository.int("conflictedCount")).isZero()
      assertThat(repository.string("currentBranch")).isNotBlank()

      val entriesByPath = repository.entries().associateBy { entry ->
        entry.jsonObject.string("pathRelativeToRepository")
      }

      assertThat(entriesByPath).containsKeys("tracked.txt", "staged.txt", "untracked.txt")
      assertThat(entriesByPath.getValue("tracked.txt").jsonObject.string("workTreeStatus")).isEqualTo("M")
      assertThat(entriesByPath.getValue("staged.txt").jsonObject.string("indexStatus")).isEqualTo("A")
      assertThat(entriesByPath.getValue("untracked.txt").jsonObject.string("indexStatus")).isEqualTo("?")
      assertThat(entriesByPath.getValue("untracked.txt").jsonObject.string("workTreeStatus")).isEqualTo("?")
    }
  }

  @Test
  fun git_status_respects_limit_and_sets_hasMoreEntries() = runBlocking {
    val repositoryPath = project.projectDirectory
    cleanupExistingGitRepository(repositoryPath)
    setupGitRepository(repositoryPath)
    createCommittedBaseline(repositoryPath)

    for (index in 1..5) {
      repositoryPath.resolve("untracked-$index.txt").writeText("$index\n")
    }

    testMcpTool(
      VcsToolset::git_status.name,
      buildJsonObject {
        put("limit", JsonPrimitive(2))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      val repository = response.repositories().first().jsonObject

      assertThat(repository.int("totalEntries")).isEqualTo(5)
      assertThat(repository.bool("hasMoreEntries")).isTrue()
      assertThat(repository.entries()).hasSize(2)
      assertThat(repository.int("untrackedCount")).isEqualTo(5)
    }
  }

  @Test
  fun git_status_filters_by_repository_path() = runBlocking {
    val repositoryPath = project.projectDirectory
    cleanupExistingGitRepository(repositoryPath)
    setupGitRepository(repositoryPath)
    createCommittedBaseline(repositoryPath)
    repositoryPath.resolve("untracked.txt").writeText("x\n")

    testMcpTool(
      VcsToolset::git_status.name,
      buildJsonObject {
        put("repositoryPathRelativeToProject", JsonPrimitive("tracked.txt"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      val repositories = response.repositories()

      assertThat(repositories).hasSize(1)
      val repository = repositories.first().jsonObject
      assertThat(repository.int("totalEntries")).isEqualTo(1)
      assertThat(repository.entries().first().jsonObject.string("pathRelativeToRepository")).isEqualTo("untracked.txt")
    }
  }

  @Test
  fun git_status_rejects_non_positive_limit() = runBlocking {
    cleanupExistingGitRepository(project.projectDirectory)

    testMcpTool(
      VcsToolset::git_status.name,
      buildJsonObject {
        put("limit", JsonPrimitive(0))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("limit must be > 0")
    }
  }

  private fun setupGitRepository(repositoryPath: Path) {
    runGit(repositoryPath, "init")
    runGit(repositoryPath, "config", "user.email", "mcp-test@example.com")
    runGit(repositoryPath, "config", "user.name", "Mcp Test")
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repositoryPath.resolve(".git"))
  }

  private fun cleanupExistingGitRepository(repositoryPath: Path) {
    val gitDirectory = repositoryPath.resolve(".git")
    if (!gitDirectory.exists()) return

    runGit(repositoryPath, "reset", "--hard")
    runGit(repositoryPath, "clean", "-fd")
  }

  private fun createCommittedBaseline(repositoryPath: Path) {
    repositoryPath.resolve("tracked.txt").writeText("base\n")
    runGit(repositoryPath, "add", "-A")
    runGit(repositoryPath, "commit", "-m", "init")
  }

  private fun runGit(repositoryPath: Path, vararg args: String): String {
    val process = GeneralCommandLine("git", *args)
      .withWorkingDirectory(repositoryPath)
      .withRedirectErrorStream(true)
      .createProcess()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    assertThat(exitCode)
      .describedAs("git ${args.joinToString(" ")} failed with output: $output")
      .isEqualTo(0)
    return output
  }

  private fun parseResult(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  private fun JsonObject.repositories() = this["repositories"]!!.jsonArray

  private fun JsonObject.entries() = this["entries"]!!.jsonArray

  private fun JsonObject.int(name: String) = this[name]!!.jsonPrimitive.content.toInt()

  private fun JsonObject.bool(name: String) = this[name]!!.jsonPrimitive.content.toBooleanStrict()

  private fun JsonObject.string(name: String) = this[name]!!.jsonPrimitive.content
}

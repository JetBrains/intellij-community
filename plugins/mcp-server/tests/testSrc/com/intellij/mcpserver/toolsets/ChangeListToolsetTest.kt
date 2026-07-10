// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.vcs.ChangeListToolset
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

class ChangeListToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun create_changelist_creates_and_activates_changelist() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("MCP Chat 1"))
        put("comment", JsonPrimitive("Changes made in chat 1"))
        put("activate", JsonPrimitive(true))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.string("name")).isEqualTo("MCP Chat 1")
      assertThat(response.string("id")).isNotBlank()
      assertThat(response.bool("created")).isTrue()
      assertThat(response.bool("isActive")).isTrue()
      assertThat(response.string("previousActiveChangeList")).isNotEqualTo("MCP Chat 1").isNotBlank()
    }

    testMcpTool(
      ChangeListToolset::get_changelists.name,
      buildJsonObject {},
    ) { result ->
      assertThat(result.isError).isFalse()
      val changeLists = parseResult(result.textContent.text).changeLists()
      val byName = changeLists.associateBy { it.jsonObject.string("name") }
      assertThat(byName).containsKey("MCP Chat 1")

      val created = byName.getValue("MCP Chat 1").jsonObject
      assertThat(created.bool("isActive")).isTrue()
      assertThat(created.string("comment")).isEqualTo("Changes made in chat 1")
      assertThat(created.int("changesCount")).isZero()

      val inactiveLists = changeLists.map { it.jsonObject }.filter { !it.bool("isActive") }
      assertThat(inactiveLists).isNotEmpty() // the former default changelist is still there
    }
  }

  @Test
  fun create_changelist_returns_existing_changelist() = runBlocking(Dispatchers.Default) {
    var createdId: String? = null
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("Shared list"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.bool("created")).isTrue()
      assertThat(response.bool("isActive")).isFalse()
      createdId = response.string("id")
    }

    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("Shared list"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.bool("created")).isFalse()
      assertThat(response.string("id")).isEqualTo(createdId)
    }
  }

  @Test
  fun delete_changelist_removes_changelist() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("To delete"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
    }

    testMcpTool(
      ChangeListToolset::delete_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("To delete"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.string("name")).isEqualTo("To delete")
      assertThat(response.int("movedChangesCount")).isZero()
    }

    testMcpTool(
      ChangeListToolset::get_changelists.name,
      buildJsonObject {},
    ) { result ->
      assertThat(result.isError).isFalse()
      val names = parseResult(result.textContent.text).changeLists().map { it.jsonObject.string("name") }
      assertThat(names).doesNotContain("To delete")
    }

    testMcpTool(
      ChangeListToolset::delete_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("To delete"))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("not found")
    }
  }

  @Test
  fun delete_changelist_rejects_active_changelist() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("Active list"))
        put("activate", JsonPrimitive(true))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
    }

    testMcpTool(
      ChangeListToolset::delete_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("Active list"))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("Cannot delete the active changelist")
    }
  }

  @Test
  fun set_active_changelist_switches_active_changelist() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("List A"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(parseResult(result.textContent.text).bool("isActive")).isFalse()
    }

    var previousActive: String? = null
    testMcpTool(
      ChangeListToolset::set_active_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("List A"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.string("name")).isEqualTo("List A")
      previousActive = response.string("previousActiveChangeList")
      assertThat(previousActive).isNotEqualTo("List A").isNotBlank()
    }

    // activating the already active changelist reports no previous changelist
    testMcpTool(
      ChangeListToolset::set_active_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("List A"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(parseResult(result.textContent.text)["previousActiveChangeList"]).isNull()
    }

    // the previously active changelist can be restored
    testMcpTool(
      ChangeListToolset::set_active_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive(previousActive!!))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(parseResult(result.textContent.text).string("previousActiveChangeList")).isEqualTo("List A")
    }

    testMcpTool(
      ChangeListToolset::set_active_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("No such list"))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("not found")
    }
  }

  @Test
  fun move_changes_to_changelist_fails_without_pending_changes() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::move_changes_to_changelist.name,
      buildJsonObject {
        put("files", JsonArray(listOf(JsonPrimitive("src/Main.java"))))
        put("changeListName", JsonPrimitive("Task changes"))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("No pending changes found")
    }
  }

  @Test
  fun move_changes_to_changelist_moves_pending_change_to_target() = runBlocking(Dispatchers.Default) {
    val repositoryPath = project.projectDirectory
    setupGitRepository(repositoryPath)

    val mainJavaPath = repositoryPath.resolve("src/Main.java")
    mainJavaPath.writeText("public class Main { }\n")
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mainJavaPath)?.refresh(false, false)

    withContext(Dispatchers.EDT) {
      ProjectLevelVcsManager.getInstance(project)
        .setDirectoryMappings(listOf(VcsDirectoryMapping(repositoryPath.pathString, "Git")))
    }
    awaitPendingChange("src/Main.java")

    testMcpTool(
      ChangeListToolset::move_changes_to_changelist.name,
      buildJsonObject {
        put("files", JsonArray(listOf(JsonPrimitive("src/Main.java"))))
        put("changeListName", JsonPrimitive("Task changes"))
        put("comment", JsonPrimitive("Changes of the current task"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.string("changeListName")).isEqualTo("Task changes")
      assertThat(response.bool("createdChangeList")).isTrue()
      assertThat(response["movedFiles"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("src/Main.java")
      assertThat(response["filesWithoutPendingChanges"]).isNull()
    }

    testMcpTool(
      ChangeListToolset::get_changelists.name,
      buildJsonObject {
        put("includeFiles", JsonPrimitive(true))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val target = parseResult(result.textContent.text).changeLists()
        .map { it.jsonObject }
        .single { it.string("name") == "Task changes" }
      assertThat(target.bool("isActive")).isFalse() // moving does not change the active changelist
      assertThat(target.int("changesCount")).isEqualTo(1)
      assertThat(target["files"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("src/Main.java")
    }

    testMcpTool(
      ChangeListToolset::delete_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("Task changes"))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      val response = parseResult(result.textContent.text)
      assertThat(response.int("movedChangesCount")).isEqualTo(1)
      assertThat(response.string("movedTo")).isNotBlank()
    }
  }

  @Test
  fun create_changelist_rejects_blank_name() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ChangeListToolset::create_changelist.name,
      buildJsonObject {
        put("name", JsonPrimitive("   "))
      },
    ) { result ->
      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).contains("must not be blank")
    }
  }

  private fun setupGitRepository(repositoryPath: Path) {
    runGit(repositoryPath, "init")
    runGit(repositoryPath, "config", "user.email", "mcp-test@example.com")
    runGit(repositoryPath, "config", "user.name", "Mcp Test")
    runGit(repositoryPath, "add", "-A")
    runGit(repositoryPath, "commit", "-m", "init")
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repositoryPath.resolve(".git"))
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

  /** Waits until [ChangeListManager][com.intellij.openapi.vcs.changes.ChangeListManager] reports a pending change for [relativePath]. */
  private suspend fun awaitPendingChange(relativePath: String) {
    repeat(30) {
      var found = false
      testMcpTool(
        ChangeListToolset::get_changelists.name,
        buildJsonObject {
          put("includeFiles", JsonPrimitive(true))
        },
      ) { result ->
        if (result.isError != true) {
          found = parseResult(result.textContent.text).changeLists().any { list ->
            list.jsonObject["files"]?.jsonArray?.any { it.jsonPrimitive.content == relativePath } == true
          }
        }
      }
      if (found) return
      delay(500.milliseconds)
    }
    error("Pending change for $relativePath was not detected by ChangeListManager")
  }

  private fun parseResult(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  private fun JsonObject.changeLists() = this["changeLists"]!!.jsonArray

  private fun JsonObject.int(name: String) = this[name]!!.jsonPrimitive.content.toInt()

  private fun JsonObject.bool(name: String) = this[name]!!.jsonPrimitive.content.toBooleanStrict()

  private fun JsonObject.string(name: String) = this[name]!!.jsonPrimitive.content
}

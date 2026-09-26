@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.RefactoringToolset
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RefactoringToolsetTest : GeneralMcpToolsetTestBase() {
  private val json = Json { ignoreUnknownKeys = true }

  private val shadowHostSource = """
    |public class ShadowHost {
    |  private int counter;
    |
    |  void bump(int counter) {
    |    this.counter = counter;
    |  }
    |}
    |""".trimMargin()

  private val shadowHost by sourceRootFixture.virtualFileFixture("ShadowHost.java", shadowHostSource)

  /**
   * Three declarations named `counter`, and a snippet that shows the second and the third.
   *
   * The declarations of the file and the candidates of the snippet are two different lists, and
   * `first`'s parameter makes them disagree on every index. So a `targetIndex` that the tool answers
   * from the wrong list renames the wrong symbol here, and the test sees it.
   */
  private val indexHostSource = """
    |public class IndexHost {
    |  void first(int counter) {
    |    System.out.println(counter);
    |  }
    |
    |  private int counter;
    |
    |  void reset(int counter) {
    |    this.counter = counter;
    |  }
    |}
    |""".trimMargin()

  private val indexHost by sourceRootFixture.virtualFileFixture("IndexHost.java", indexHostSource)

  private val renameMeSource = """
    |public class RenameMe {
    |  void run() { }
    |}
    |""".trimMargin()

  private val renameMe by sourceRootFixture.virtualFileFixture("RenameMe.java", renameMeSource)

  /** A method named after the word in its own comment. The comment holds no reference to it. */
  private val commentHostSource = """
    |public class CommentHost {
    |  int counter() {
    |    // counter here
    |    return 0;
    |  }
    |}
    |""".trimMargin()

  private val commentHost by sourceRootFixture.virtualFileFixture("CommentHost.java", commentHostSource)

  @BeforeEach
  fun waitForIndexes() {
    DumbService.getInstance(project).waitForSmartMode()
  }

  @Test
  fun `java language support is available`() {
    assertThat(PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.java")))
      .describedAs("The rename tests need Java PSI. Add the Java plugin modules to this test module.")
      .isTrue()
  }

  @Test
  fun `ambiguous name returns candidates and changes nothing`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("ambiguous_symbol")

    val candidates = result.candidates
    assertThat(candidates).isNotNull()
    assertThat(candidates!!.size).describedAs("The field and the parameter must both be reported").isGreaterThanOrEqualTo(2)
    assertThat(candidates.map { it.targetIndex })
      .describedAs("targetIndex must be 1-based and dense, so the caller can pick one")
      .isEqualTo((1..candidates.size).toList())
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `context snippet selects the field and not the parameter`(): Unit = runBlocking(Dispatchers.Default) {
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("contextSnippet", JsonPrimitive("private int counter;"))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()

    val text = shadowHost.text()
    assertThat(text).contains("private int hitCount;")
    // The parameter shadows the field, so it must keep its own name.
    assertThat(text).contains("void bump(int counter)")
    assertThat(text).contains("this.hitCount = counter;")
  }

  @Test
  fun `context snippet selects the parameter and not the field`(): Unit = runBlocking(Dispatchers.Default) {
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("delta"))
      put("contextSnippet", JsonPrimitive("void bump(int counter)"))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()

    val text = shadowHost.text()
    assertThat(text).contains("void bump(int delta)")
    assertThat(text).contains("private int counter;")
    assertThat(text).contains("this.counter = delta;")
  }

  @Test
  fun `stale context snippet refuses and changes nothing`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("contextSnippet", JsonPrimitive("private long counter;"))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("snippet_not_found")
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `position that disagrees with symbol name refuses`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val (line, column) = positionOf(shadowHostSource, "bump")
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("line", JsonPrimitive(line))
      put("column", JsonPrimitive(column))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("name_mismatch")
    assertThat(result.error?.hint).contains("bump")
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `position selects the field`(): Unit = runBlocking(Dispatchers.Default) {
    val (line, column) = positionOf(shadowHostSource, "counter;")
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("line", JsonPrimitive(line))
      put("column", JsonPrimitive(column))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()
    assertThat(shadowHost.text()).contains("private int hitCount;")
    assertThat(shadowHost.text()).contains("void bump(int counter)")
  }

  @Test
  fun `target index picks the reported candidate`(): Unit = runBlocking(Dispatchers.Default) {
    val ambiguous = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
    }
    val fieldIndex = ambiguous.candidates
      ?.first { it.symbol?.declarationText?.contains("private int counter") == true }
      ?.targetIndex
    assertThat(fieldIndex).isNotNull()

    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("targetIndex", JsonPrimitive(fieldIndex))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()
    assertThat(shadowHost.text()).contains("private int hitCount;")
  }

  @Test
  fun `preview reports the blast radius and writes nothing`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("contextSnippet", JsonPrimitive("private int counter;"))
      put("preview", JsonPrimitive(true))
    }
    assertThat(result.ok).isTrue()
    assertThat(result.applied).isFalse()

    val affects = result.affects
    assertThat(affects).isNotNull()
    assertThat(affects!!.files).isGreaterThanOrEqualTo(1)
    assertThat(affects.usages).isGreaterThanOrEqualTo(1)
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `rename to the current name refuses`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("counter"))
      put("contextSnippet", JsonPrimitive("private int counter;"))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("new_name_matches_current")
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `invalid new name refuses`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("not a name"))
      put("contextSnippet", JsonPrimitive("private int counter;"))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("new_name_invalid")
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `unknown symbol refuses`(): Unit = runBlocking(Dispatchers.Default) {
    val result = callRename {
      put("symbolName", JsonPrimitive("thereIsNoSuchSymbol"))
      put("newName", JsonPrimitive("stillNone"))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("symbol_not_found")
  }

  @Test
  fun `target index picks from the list the snippet reported`(): Unit = runBlocking(Dispatchers.Default) {
    val snippet = "this.counter = counter;"
    val ambiguous = callRename(indexHost) {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("delta"))
      put("contextSnippet", JsonPrimitive(snippet))
    }
    assertThat(ambiguous.error?.kind).isEqualTo("ambiguous_symbol")
    assertThat(ambiguous.candidates)
      .describedAs("The snippet shows the field and the parameter of reset, and nothing else")
      .hasSize(2)

    // Candidate 2 is the parameter of reset. The declarations of the file put the parameter of
    // first at 1 and the field at 2, so an index read from that list would rename the field.
    val result = callRename(indexHost) {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("delta"))
      put("contextSnippet", JsonPrimitive(snippet))
      put("targetIndex", JsonPrimitive(2))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()

    val text = indexHost.text()
    assertThat(text).contains("void reset(int delta)")
    assertThat(text).contains("this.counter = delta;")
    assertThat(text).describedAs("The field keeps its name").contains("private int counter;")
    assertThat(text).describedAs("The parameter of first keeps its name").contains("void first(int counter)")
  }

  @Test
  fun `column past the end of the line refuses`(): Unit = runBlocking(Dispatchers.Default) {
    val before = shadowHost.text()
    val (line, _) = positionOf(shadowHostSource, "private int counter;")
    val result = callRename {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("line", JsonPrimitive(line))
      // Past the end of the line. It must not walk on to the next line, which also holds 'counter'.
      put("column", JsonPrimitive(400))
    }
    assertThat(result.ok).isFalse()
    assertThat(result.applied).isFalse()
    assertThat(result.error?.kind).isEqualTo("position_out_of_bounds")
    assertThat(shadowHost.text()).isEqualTo(before)
  }

  @Test
  fun `renaming a public class renames its file`(): Unit = runBlocking(Dispatchers.Default) {
    val result = callRename(renameMe) {
      put("symbolName", JsonPrimitive("RenameMe"))
      put("newName", JsonPrimitive("Renamed"))
    }
    assertThat(result.error).isNull()
    assertThat(result.applied).isTrue()

    val renamedFile = result.renamedFile
    assertThat(renamedFile).describedAs("The file follows the public class, so the caller must be told").isNotNull()
    assertThat(renamedFile!!.previousPath).endsWith("RenameMe.java")
    assertThat(renamedFile.path).endsWith("Renamed.java")
    assertThat(renameMe.text()).contains("public class Renamed")
  }

  @Test
  fun `a position in a comment does not select the enclosing declaration`(): Unit = runBlocking(Dispatchers.Default) {
    val before = commentHost.text()
    val (line, column) = positionOf(commentHostSource, "counter here")
    val result = callRename(commentHost) {
      put("symbolName", JsonPrimitive("counter"))
      put("newName", JsonPrimitive("hitCount"))
      put("line", JsonPrimitive(line))
      put("column", JsonPrimitive(column))
    }
    assertThat(result.applied)
      .describedAs("The comment holds no reference, so the method it sits in is not the target")
      .isFalse()
    assertThat(result.error?.kind).isEqualTo("symbol_not_found")
    assertThat(commentHost.text()).isEqualTo(before)
  }

  private suspend fun callRename(file: VirtualFile = shadowHost, arguments: JsonObjectBuilderScope): RenameResult {
    var parsed: RenameResult? = null
    testMcpTool(
      RefactoringToolset::rename_refactoring.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("src/${file.name}"))
        arguments()
      },
    ) { result -> parsed = parse(result) }
    return parsed ?: error("The tool returned no result")
  }

  private fun parse(result: CallToolResult): RenameResult {
    assertThat(result.isError).describedAs("Unexpected MCP error: %s", result.textContent.text).isFalse()
    return json.decodeFromString(RenameResult.serializer(), result.textContent.text)
  }

  /** The 1-based line and column of [marker] in [content], computed instead of counted by hand. */
  private fun positionOf(content: String, marker: String): Pair<Int, Int> {
    val index = content.indexOf(marker)
    assertThat(index).describedAs("'%s' is not in the fixture", marker).isNotNegative()
    val lineStart = content.lastIndexOf('\n', index - 1) + 1
    val line = content.take(index).count { it == '\n' } + 1
    return line to (index - lineStart + 1)
  }

  private fun VirtualFile.text(): String = String(contentsToByteArray(), Charsets.UTF_8)

  @Serializable
  private data class RenameResult(
    val ok: Boolean = false,
    val applied: Boolean = false,
    val resolvedSymbol: SymbolInfo? = null,
    val affects: Affects? = null,
    val renamedFile: RenamedFile? = null,
    val candidates: List<Candidate>? = null,
    val conflicts: List<Conflict>? = null,
    val error: Error? = null,
  )

  @Serializable
  private data class Affects(val files: Int = 0, val usages: Int = 0)

  @Serializable
  private data class RenamedFile(val previousPath: String = "", val path: String = "")

  @Serializable
  private data class SymbolInfo(val name: String? = null, val declarationText: String = "")

  @Serializable
  private data class Candidate(val targetIndex: Int = 0, val kind: String? = null, val symbol: SymbolInfo? = null)

  @Serializable
  private data class Conflict(val description: String = "", val filePath: String? = null)

  @Serializable
  private data class Error(val kind: String = "", val hint: String = "")
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit

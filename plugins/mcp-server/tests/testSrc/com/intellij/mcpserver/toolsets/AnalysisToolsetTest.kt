@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.AnalysisToolset
import com.intellij.mcpserver.toolsets.general.RequestedLintFile
import com.intellij.mcpserver.toolsets.general.prepareLintFiles
import com.intellij.mcpserver.toolsets.general.prepareRequestedLintFiles
import com.intellij.mcpserver.toolsets.general.withLintFilesCollectorOverride
import com.intellij.mcpserver.util.attachJarLibrary
import com.intellij.mcpserver.util.INDEXING_PARTIAL_RESULT_REASON
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
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
  private val commonsCsvJar by projectFixture.fileOrDirInProjectFixture("libraries/commons-csv/commons-csv-1.14.1.jar")

  @Test
  fun analyze_calls_renders_outgoing_tree() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.root"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(2))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("└─")
      assertThat(text).contains("filePath=\"src/calls/CallGraph.java\"")
      assertThat(text).contains("treePath=[]")
      assertThat(text).contains("treePath=[\"calls.CallGraph.")
      assertThat(text).contains("root")
      assertThat(text).contains("first")
      assertThat(text).contains("second")
      assertThat(text).contains("2 usages")
      assertThat(text).doesNotContain("Deprecated")
      assertThat(text).doesNotContain("String")
      assertThat(text).doesNotContain("NAME")
    }
  }

  @Test
  fun analyze_calls_renders_incoming_tree() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.leaf"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.INCOMING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("leaf")
      assertThat(text).contains("first")
      assertThat(text).contains("treePath=[\"calls.CallGraph.first()\"")
    }
  }

  @Test
  fun analyze_calls_reports_ambiguity_with_exact_signatures() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    var exactSignature: String? = null
    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.overloaded"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isTrue()
      assertThat(text).contains("Ambiguous symbolFqn")
      assertThat(text).contains("overloaded")
      assertThat(text).contains("symbolFqn: calls.CallGraph.overloaded()")
      assertThat(text).contains("symbolFqn: calls.CallGraph.overloaded(String)")
      exactSignature = Regex("symbolFqn: (calls\\.CallGraph\\.overloaded\\([^)]*\\))").find(text)?.groupValues?.get(1)
    }

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive(requireNotNull(exactSignature)))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(0))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(result.textContent.text).contains("overloaded")
    }
  }

  @Test
  fun analyze_calls_renders_child_limit_with_more_line() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.root"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
        put("maxChildren", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("… and 1 more")
      assertThat(text).contains("childOffset=1")
    }
  }

  @Test
  fun analyze_calls_prepends_partial_result_note_during_indexing() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")
    DumbService.getInstance(project).waitForSmartMode()

    val token = DumbModeTestUtils.startEternalDumbModeTask(project)
    try {
      testMcpTool(
        AnalysisToolset::analyze_calls.name,
        buildJsonObject {
          put("symbolFqn", JsonPrimitive("calls.CallGraph.root"))
          put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
          put("depth", JsonPrimitive(1))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(result.isError).isFalse()
        assertThat(text).startsWith("> Note: $INDEXING_PARTIAL_RESULT_REASON")
      }
    }
    finally {
      DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token)
    }
  }

  @Test
  fun analyze_calls_reports_child_offset_past_last_child() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.root"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
        put("childOffset", JsonPrimitive(999))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("root")
      assertThat(text).contains("… no more children")
      assertThat(text).contains("childOffset=999")
      assertThat(text).contains("totalChildren=2")
    }
  }

  @Test
  fun analyze_calls_reports_max_nodes_limit_with_more_line() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.root"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
        put("maxNodes", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("root")
      assertThat(text).contains("… and 2 more")
      assertThat(text).contains("childOffset=0")
    }
  }

  @Test
  fun analyze_calls_accepts_java_class_root_as_default_constructor() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(0))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(result.textContent.text).contains("CallGraph()")
    }
  }

  @Test
  fun analyze_calls_uses_language_provider_target_for_type_root() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("CallGraph()")
      assertThat(text).doesNotContain("root()")
      assertThat(text).doesNotContain("first()")
    }

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.INCOMING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("CallGraph()")
      assertThat(text).contains("createInstance()")
    }
  }

  @Test
  fun analyze_calls_resolves_binary_jar_dependency_root() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")
    attachJarLibrary(project, moduleFixture.get(), commonsCsvJar, libraryName = "commons-csv")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("org.apache.commons.csv.CSVParser.builder()"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.INCOMING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("builder")
      assertThat(text).contains("callsBinaryJar")
    }
  }

  @Test
  fun analyze_calls_resolves_each_short_exact_overload_signature() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("CallGraph.overloaded()"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("overloaded()")
      assertThat(text).contains("first()")
      assertThat(text).doesNotContain("second()")
    }

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("CallGraph.overloaded(String)"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("overloaded(String)")
      assertThat(text).contains("second()")
      assertThat(text).doesNotContain("first()")
    }
  }

  @Test
  fun analyze_calls_accepts_short_class_method_and_exact_signature() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("CallGraph.first"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(0))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(result.textContent.text).contains("first")
    }

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("calls.CallGraph.overloaded(String)"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(0))
      },
    ) { result ->
      assertThat(result.isError).isFalse()
      assertThat(result.textContent.text).contains("overloaded(String)")
    }
  }

  @Test
  fun analyze_calls_resolves_kotlin_member_by_full_fqn() = runBlocking(Dispatchers.Default) {
    assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("kotlinCalls.KotlinCallGraph.memberEntry"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("memberEntry")
      assertThat(text).contains("topLeaf")
    }
  }

  @Test
  fun analyze_calls_resolves_kotlin_top_level_function_by_source_fqn() = runBlocking(Dispatchers.Default) {
    assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("kotlinCalls.topEntry"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("topEntry")
      assertThat(text).contains("topLeaf")
    }
  }

  @Test
  fun analyze_calls_resolves_kotlin_top_level_function_by_jvm_facade_fqn() = runBlocking(Dispatchers.Default) {
    assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("kotlinCalls.KotlinCallGraphKt.fileFacadeEntry"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
        put("depth", JsonPrimitive(1))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isFalse()
      assertThat(text).contains("fileFacadeEntry")
      assertThat(text).contains("topLeaf")
    }
  }

  @Test
  fun analyze_calls_keeps_short_kotlin_name_ambiguous() = runBlocking(Dispatchers.Default) {
    assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("ambiguousKotlinEntry"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isTrue()
      assertThat(text).contains("Ambiguous symbolFqn")
    }
  }

  @Test
  fun analyze_calls_treats_legacy_persisted_signature_as_plain_symbol_input() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::analyze_calls.name,
      buildJsonObject {
        put("symbolFqn", JsonPrimitive("ij-call:v1:invalid"))
        put("analysisKind", JsonPrimitive(AnalysisToolset.AnalysisKind.OUTGOING_CALLS.name))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(result.isError).isTrue()
      assertThat(text).contains("No callable symbol found")
      assertThat(text).doesNotContain("Invalid persisted symbol signature")
    }
  }

  @Test
  fun lint_files() = runBlocking(Dispatchers.Default) {
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    testMcpTool(
      AnalysisToolset::lint_files.name,
      buildJsonObject {
        put("files", buildJsonArray {
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
        put("files", buildJsonArray {
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
          put("files", buildJsonArray {
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
          put("files", buildJsonArray {
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
  fun lint_files_includes_timed_out_files_and_omits_clean_files() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(AnalysisToolset.LintFileResult(filePath = mainPath, timedOut = true))
        onFileResult(AnalysisToolset.LintFileResult(filePath = classPath))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("files", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).containsOnlyOnce(""""filePath":"src/Main.java"""")
        assertThat(text).contains(""""timedOut":true""")
        assertThat(text).contains(""""problems":[]""")
        assertThat(text).doesNotContain(""""filePath":"src/Class.java"""")
        assertThat(text).doesNotContain(""""more":true""")
      }
    }
  }

  @Test
  fun lint_files_keeps_timed_out_files_in_request_order() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(lintFileResultWithProblem(classPath))
        onFileResult(AnalysisToolset.LintFileResult(filePath = mainPath, timedOut = true))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("files", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text.indexOf(""""filePath":"src/Main.java"""")).isLessThan(text.indexOf(""""filePath":"src/Class.java"""))
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
          put("files", buildJsonArray {
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
  fun lint_files_includes_not_analyzed_files() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)
    val classPath = project.projectDirectory.relativizeIfPossible(classJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(lintFileResultWithProblem(mainPath))
        onFileResult(AnalysisToolset.LintFileResult(filePath = classPath, notAnalyzedReason = "File is outside project content roots or in an excluded directory"))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("files", buildJsonArray {
            add(JsonPrimitive(mainPath))
            add(JsonPrimitive(classPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).containsOnlyOnce(""""filePath":"src/Main.java"""")
        assertThat(text).containsOnlyOnce(""""filePath":"src/Class.java"""")
        assertThat(text).contains(""""notAnalyzedReason":"File is outside project content roots or in an excluded directory"""")
      }
    }
  }

  @Test
  fun lint_files_omits_not_analyzed_fields_for_clean_files() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(lintFileResultWithProblem(mainPath))
      },
    ) {
      testMcpTool(
        AnalysisToolset::lint_files.name,
        buildJsonObject {
          put("files", buildJsonArray {
            add(JsonPrimitive(mainPath))
          })
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).containsOnlyOnce(""""filePath":"src/Main.java"""")
        assertThat(text).doesNotContain(""""notAnalyzed"""")
        assertThat(text).doesNotContain(""""notAnalyzedReason"""")
      }
    }
  }

  @Test
  fun get_file_problems_fails_for_not_analyzed_file() = runBlocking(Dispatchers.Default) {
    val mainPath = project.projectDirectory.relativizeIfPossible(mainJavaFile)

    withLintFilesCollector(
      collector = { _, onFileResult ->
        onFileResult(AnalysisToolset.LintFileResult(filePath = mainPath, notAnalyzedReason = "File is outside project content roots or in an excluded directory"))
      },
    ) {
      testMcpTool(
        AnalysisToolset::get_file_problems.name,
        buildJsonObject {
          put("filePath", JsonPrimitive(mainPath))
          put("errorsOnly", JsonPrimitive(false))
        },
      ) { result ->
        assertThat(result.isError).isTrue()
        assertThat(result.textContent.text).contains("File cannot be analyzed")
        assertThat(result.textContent.text).contains("File is outside project content roots or in an excluded directory")
      }
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

  private fun isKotlinPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("org.jetbrains.kotlin"))
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

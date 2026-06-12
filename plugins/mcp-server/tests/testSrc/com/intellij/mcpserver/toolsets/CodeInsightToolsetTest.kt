@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.CodeInsightToolset
import com.intellij.mcpserver.util.INDEXING_PARTIAL_RESULT_REASON
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeInsightToolsetTest : GeneralMcpToolsetTestBase() {
  private val json = Json { ignoreUnknownKeys = true }

  private val symbolFile by sourceRootFixture.virtualFileFixture(
    "ci_symbol_info_43c1.kt",
    "class CiSymbolInfo43c1 {}\n",
  )

  @Serializable
  private data class SymbolInfoResult(
    val documentation: String = "",
    val partialResultReason: String? = null,
  )

  private fun parseResult(text: String?): SymbolInfoResult {
    val payload = text ?: error("Tool call result should include text content")
    return json.decodeFromString(SymbolInfoResult.serializer(), payload)
  }

  @Test
  fun get_symbol_info_reports_partial_result_during_indexing() = runBlocking(Dispatchers.Default) {
    // Force the test project file into existence and indexes built, then enter "fake" dumb mode
    // so the tool sees `isDumb == true` while its read action still has working indexes.
    val relativePath = "src/${symbolFile.name}"
    edtWriteAction { /* touch fixture */ }
    DumbService.getInstance(project).waitForSmartMode()

    val token = DumbModeTestUtils.startEternalDumbModeTask(project)
    try {
      testMcpTool(
        CodeInsightToolset::get_symbol_info.name,
        buildJsonObject {
          put("filePath", JsonPrimitive(relativePath))
          put("line", JsonPrimitive(1))
          put("column", JsonPrimitive(7))
        },
      ) { actualResult ->
        assertThat(actualResult.isError).isFalse()
        val result = parseResult(actualResult.textContent.text)
        assertThat(result.partialResultReason).isEqualTo(INDEXING_PARTIAL_RESULT_REASON)
      }
    }
    finally {
      DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token)
    }
  }

  @Test
  fun get_symbol_info_omits_partial_result_in_smart_mode() = runBlocking(Dispatchers.Default) {
    val relativePath = "src/${symbolFile.name}"
    DumbService.getInstance(project).waitForSmartMode()

    testMcpTool(
      CodeInsightToolset::get_symbol_info.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(relativePath))
        put("line", JsonPrimitive(1))
        put("column", JsonPrimitive(7))
      },
    ) { actualResult ->
      assertThat(actualResult.isError).isFalse()
      val result = parseResult(actualResult.textContent.text)
      assertThat(result.partialResultReason).isNull()
    }
  }
}

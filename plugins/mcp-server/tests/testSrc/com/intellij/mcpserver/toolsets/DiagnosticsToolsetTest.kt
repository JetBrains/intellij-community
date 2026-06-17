@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.DiagnosticsToolset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val DIAGNOSTICS_ENABLED_PROPERTY = "idea.diagnostics.mcp.enabled"

class DiagnosticsToolsetTest : GeneralMcpToolsetTestBase() {
  @AfterEach
  fun clearDiagnosticsProperty() {
    System.clearProperty(DIAGNOSTICS_ENABLED_PROPERTY)
  }

  @Test
  fun diagnostics_toolset_is_disabled_by_default() {
    assertThat(DiagnosticsToolset().isEnabled()).isFalse()
  }

  @Test
  fun diagnostics_toolset_is_enabled_by_system_property() {
    System.setProperty(DIAGNOSTICS_ENABLED_PROPERTY, "true")
    assertThat(DiagnosticsToolset().isEnabled()).isTrue()
  }

  @Test
  fun get_ide_diagnostics_returns_snapshot_without_sampling_delay() = runBlocking(Dispatchers.Default) {
    withRegisteredDiagnosticsTool {
      testMcpTool(
        DiagnosticsToolset::get_ide_diagnostics.name,
        buildJsonObject {
          put("sampleMillis", JsonPrimitive(0))
          put("includeRawDump", JsonPrimitive(false))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(result.isError).isFalse()
        assertThat(text).contains("\"sampleMillis\":0")
        assertThat(text).contains("\"memory\":{")
        assertThat(text).contains("\"threads\":{")
        assertThat(text).contains("\"topCpuThreads\":[")
        assertThat(text).contains("\"coroutineDumpEnabled\":")
        assertThat(text).doesNotContain("\"rawDump\":")
      }
    }
  }

  @Test
  fun get_ide_diagnostics_clamps_inputs_and_truncates_raw_dump() = runBlocking(Dispatchers.Default) {
    withRegisteredDiagnosticsTool {
      testMcpTool(
        DiagnosticsToolset::get_ide_diagnostics.name,
        buildJsonObject {
          put("sampleMillis", JsonPrimitive(-1))
          put("topThreadCount", JsonPrimitive(0))
          put("includeRawDump", JsonPrimitive(true))
          put("maxDumpChars", JsonPrimitive(1))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(result.isError).isFalse()
        assertThat(text).contains("\"sampleMillis\":0")
        assertThat(text).contains("\"rawDump\":")
        assertThat(text).contains("\"rawDumpTruncated\":true")
      }
    }
  }

  @Test
  fun get_ide_diagnostics_samples_cpu_deltas() = runBlocking(Dispatchers.Default) {
    withRegisteredDiagnosticsTool {
      testMcpTool(
        DiagnosticsToolset::get_ide_diagnostics.name,
        buildJsonObject {
          put("sampleMillis", JsonPrimitive(10))
          put("topThreadCount", JsonPrimitive(3))
          put("includeRawDump", JsonPrimitive(false))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(result.isError).isFalse()
        assertThat(text).contains("\"sampleMillis\":10")
        assertThat(text).contains("\"cpuDeltaNanos\":")
        assertThat(text).contains("\"userDeltaNanos\":")
      }
    }
  }

  private suspend fun withRegisteredDiagnosticsTool(action: suspend () -> Unit) {
    val diagnosticsToolset = DiagnosticsToolset()
    withRegisteredTestTools(diagnosticsToolset::get_ide_diagnostics) {
      action()
    }
  }
}

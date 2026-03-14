package com.intellij.mcpserver.util

import com.intellij.mcpserver.toolsets.Constants
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutionUtilTest {
  @Test
  fun buildSessionId_returns_plain_name_for_unique_session() {
    assertEquals("run", buildSessionId("run", 42, listOf("run")))
  }

  @Test
  fun buildSessionId_appends_execution_id_for_duplicate_sessions() {
    assertEquals("run#42", buildSessionId("run", 42, listOf("run", "run")))
  }

  @Test
  fun runConfigurationOutputCollector_limits_preview_to_max_length_and_persists_full_output() = runBlocking {
    val outputFile = Files.createTempFile("execution-util-test", ".log")
    try {
      val collector = OutputCollector(this, outputFile)
      val output = "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH + 1)

      collector.append(output)
      collector.close()
      collector.waitForDrain()

      assertEquals(
        "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH) + Constants.RUN_CONFIGURATION_PREVIEW_TRUNCATED_MARKER,
        collector.getOutputPreview(),
      )
      assertTrue(collector.isOutputPreviewTruncated)
      assertEquals(output, Files.readString(outputFile))
    }
    finally {
      Files.deleteIfExists(outputFile)
    }
  }

  @Test
  fun truncateRunConfigurationPreviewLine_appends_marker_and_keeps_fixed_length() {
    val longLine = "a".repeat(2_000)

    val truncated = truncateRunConfigurationPreviewLine(longLine)

    assertTrue(truncated.endsWith("<truncated>"))
    assertEquals(1_000, truncated.length)
  }
}

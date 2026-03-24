package com.intellij.mcpserver.util

import com.intellij.mcpserver.toolsets.Constants
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ExecutionUtilTest {
  @Test
  fun buildSessionId_returns_plain_name_for_unique_session() {
    assertThat(buildSessionId("run", 42, listOf("run"))).isEqualTo("run")
  }

  @Test
  fun buildSessionId_appends_execution_id_for_duplicate_sessions() {
    assertThat(buildSessionId("run", 42, listOf("run", "run"))).isEqualTo("run#42")
  }

  @Test
  fun runConfigurationOutputCollector_limits_preview_to_max_length_and_persists_full_output() = runBlocking<Unit> {
    val outputFile = Files.createTempFile("execution-util-test", ".log")
    try {
      val collector = OutputCollector(this, outputFile)
      val output = "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH + 1)

      collector.append(output)
      collector.close()
      collector.waitForDrain()

      assertThat(collector.getOutputPreview()).isEqualTo(
        "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH) + Constants.RUN_CONFIGURATION_PREVIEW_TRUNCATED_MARKER,
      )
      assertThat(collector.isOutputPreviewTruncated).isTrue()
      assertThat(Files.readString(outputFile)).isEqualTo(output)
    }
    finally {
      Files.deleteIfExists(outputFile)
    }
  }

  @Test
  fun truncateRunConfigurationPreviewLine_appends_marker_and_keeps_fixed_length() {
    val longLine = "a".repeat(2_000)

    val truncated = truncateRunConfigurationPreviewLine(longLine)

    assertThat(truncated).endsWith("<truncated>")
    assertThat(truncated).hasSize(1_000)
  }
}

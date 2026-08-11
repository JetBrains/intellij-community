package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpServerCounterUsagesCollectorTest {
  @Test
  fun lint_files_event_has_safe_aggregate_schema() {
    val group = McpServerCounterUsagesCollector.group
    val event = group.events.single { it.eventId == "mcp.lint.files.finished" }
    val fields = event.getFields()

    assertThat(group.id).isEqualTo("mcpserver.events")
    assertThat(group.version).isEqualTo(7)
    assertThat(fields.map { it.name }).containsExactly(
      "min_severity",
      "result",
      "requested_file_count",
      "problem_file_count",
      "problem_count",
      "error_count",
      "timed_out_file_count",
      "not_analyzed_file_count",
      "more",
      "duration_ms",
    )

    val minSeverity = fields.single { it.name == "min_severity" }
    assertThat(minSeverity).isInstanceOf(StringEventField.ValidatedByAllowedValues::class.java)
    assertThat((minSeverity as StringEventField.ValidatedByAllowedValues).allowedValues)
      .containsExactly("warning", "strong_warning", "error")

    val countFields = fields.filter { it.name.endsWith("_count") }
    assertThat(countFields).allMatch { it is RoundedIntEventField }
  }

  @Test
  fun lint_files_result_classifies_complete_and_incomplete_analysis() {
    assertThat(lintFilesResultKind(problemCount = 0, timedOutFileCount = 0, notAnalyzedFileCount = 0, more = false))
      .isEqualTo(LintFilesResultKind.CLEAN)
    assertThat(lintFilesResultKind(problemCount = 2, timedOutFileCount = 0, notAnalyzedFileCount = 0, more = false))
      .isEqualTo(LintFilesResultKind.PROBLEMS_FOUND)
    assertThat(lintFilesResultKind(problemCount = 2, timedOutFileCount = 1, notAnalyzedFileCount = 0, more = false))
      .isEqualTo(LintFilesResultKind.INCOMPLETE)
    assertThat(lintFilesResultKind(problemCount = 0, timedOutFileCount = 0, notAnalyzedFileCount = 1, more = false))
      .isEqualTo(LintFilesResultKind.INCOMPLETE)
    assertThat(lintFilesResultKind(problemCount = 0, timedOutFileCount = 0, notAnalyzedFileCount = 0, more = true))
      .isEqualTo(LintFilesResultKind.INCOMPLETE)
  }
}

package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
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
    assertThat(group.version).isEqualTo(10)
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

  /**
   * A tool call is only comparable across populations if the row says who called it and how it arrived: without
   * `invocation_mode` a routed call is indistinguishable from a direct one, and without `launch_origin` an agent the
   * IDE launched is counted together with an external client whose session the IDE only partly observes.
   */
  @Test
  fun tool_call_event_reports_the_caller_and_how_the_call_arrived() {
    val event = McpServerCounterUsagesCollector.group.events.single { it.eventId == "mcp.tool.call" }

    assertThat(event.getFields().map { it.name }).contains(
      "tool_name",
      "toolset",
      "outcome",
      "duration_ms",
      "invocation_mode",
      "launch_origin",
      "client_name",
      "transport_type",
      "argument_bytes",
      "result_bytes",
    )
  }

  /**
   * A routed call produces two rows, because the router is a registered tool itself: the `execute_tool` call and the
   * call it dispatched to. The router's row has to say so, or a per-tool count double-counts every routed call and
   * inherits its nested duration.
   */
  @Test
  fun tool_call_event_separates_the_router_entry_from_the_tool_it_dispatched_to() {
    val event = McpServerCounterUsagesCollector.group.events.single { it.eventId == "mcp.tool.call" }
    val invocationMode = event.getFields().single { it.name == "invocation_mode" } as PrimitiveEventField<*>

    assertThat(invocationMode.validationRule.single())
      .contains("ROUTER_ENTRY")
      .contains("VIA_ROUTER")
      .contains("DIRECT_WITH_ROUTER_ENABLED")
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

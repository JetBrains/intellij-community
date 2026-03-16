package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpExpectedError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReadToolsetHelpersTest {
  @Test
  fun `normalizeMode trims and lowercases`() {
    assertThat(normalizeMode("  LINES ")).isEqualTo("lines")
  }

  @Test
  fun `normalizeMode rejects unknown values`() {
    assertThatThrownBy { normalizeMode("unknown") }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("mode must be one of")
  }

  @Test
  fun `normalizeMode rejects blank value`() {
    assertThatThrownBy { normalizeMode(" ") }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("mode must be one of")
  }

  @Test
  fun `capContextLines respects output cap`() {
    assertThat(capContextLines(rangeLines = 3, requestedContext = 2, maxLines = 5)).isEqualTo(1)
  }

  @Test
  fun `capContextLines returns zero when range consumes max_lines`() {
    assertThat(capContextLines(rangeLines = 5, requestedContext = 2, maxLines = 5)).isEqualTo(0)
  }

  @Test
  fun `capContextLines rejects range larger than max_lines`() {
    assertThatThrownBy { capContextLines(rangeLines = 6, requestedContext = 1, maxLines = 5) }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("range exceeds max_lines")
  }

  @Test
  fun `capContextLines rejects empty ranges`() {
    assertThatThrownBy { capContextLines(rangeLines = 0, requestedContext = 1, maxLines = 5) }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("range must be greater than zero")
  }
}

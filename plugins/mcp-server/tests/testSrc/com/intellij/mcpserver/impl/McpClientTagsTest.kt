// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpClientTagsTest {
  @Test
  fun `client tags are trimmed deduplicated and empty values are ignored`() {
    assertThat(parseMcpClientTags(" alpha, ,beta,alpha ")).containsExactly("alpha", "beta")
  }

  @Test
  fun `client tags survive session tool-filter replacement`() {
    val options = McpServerService.McpSessionOptions(
      commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
      toolFilter = null,
      localAgentId = null,
      invocationMode = null,
      elicitationKind = null,
      clientTags = setOf("air-container:session-1"),
    )

    val filtered = options.withToolFilter(McpToolFilter.AllowList(setOf("read_file")))

    assertThat(filtered.clientTags).containsExactly("air-container:session-1")
  }
}

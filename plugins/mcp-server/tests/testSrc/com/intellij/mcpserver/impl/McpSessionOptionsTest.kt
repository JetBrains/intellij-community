// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpSessionOptionsTest {
  private val options = McpServerService.McpSessionOptions(McpServerService.AskCommandExecutionMode.DONT_ASK)

  @Test
  fun `a session lists an unused router tool by default`() {
    assertThat(options.omitsUnusedRouterTool).isFalse()
  }

  @Test
  fun `the router opt-in survives every copy`() {
    val omitting = options.withUnusedRouterToolOmitted()

    assertThat(omitting.omitsUnusedRouterTool).isTrue()
    assertThat(omitting.withOwnerKey("thread-1").omitsUnusedRouterTool).isTrue()
    assertThat(omitting.withToolFilter(McpToolFilter.AllowList(setOf("read_file"))).omitsUnusedRouterTool).isTrue()
    assertThat(omitting.withClientTags(setOf("air-container:session-1")).omitsUnusedRouterTool).isTrue()
  }

  @Test
  fun `client tags replace the tags and keep the owner key`() {
    val tagged = options.withOwnerKey("thread-1").withClientTags(setOf("air-container:session-1"))

    assertThat(tagged.clientTags).containsExactly("air-container:session-1")
    assertThat(tagged.ownerKey).isEqualTo("thread-1")
  }
}

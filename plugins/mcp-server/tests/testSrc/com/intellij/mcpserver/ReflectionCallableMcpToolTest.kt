// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReflectionCallableMcpToolTest : McpToolsetTestBase() {
  @Test
  fun `missing required argument is returned as an expected error`() = runBlocking<Unit> {
    withRegisteredTestTools(::requiresQuery) {
      val result = callToolWithProgress("requiresQuery").result

      assertThat(result.isError).isTrue()
      assertThat(result.textContent.text).isEqualTo("No argument is passed for required parameter 'q'")
    }
  }
}

private fun requiresQuery(q: String): String = q

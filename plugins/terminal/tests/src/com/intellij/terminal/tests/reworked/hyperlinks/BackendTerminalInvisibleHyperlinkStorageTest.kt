// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.terminal.backend.hyperlinks.BackendTerminalInvisibleHyperlinkStorage
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.junit.Test

internal class BackendTerminalInvisibleHyperlinkStorageTest {

  private val storage = BackendTerminalInvisibleHyperlinkStorage()

  @Test
  fun `hyperlinks are found by id`() {
    val link1 = link()
    val link2 = link()
    storage.addRequestResult(requestId = 1, mapOf(id(1) to link1, id(2) to link2))

    assertThat(storage.findHyperlink(id(1))).isSameAs(link1)
    assertThat(storage.findHyperlink(id(2))).isSameAs(link2)
    assertThat(storage.findHyperlink(id(3))).isNull()
  }

  @Test
  fun `retaining requests forgets the hyperlinks of the other requests`() {
    storage.addRequestResult(requestId = 1, mapOf(id(1) to link()))
    storage.addRequestResult(requestId = 2, mapOf(id(2) to link()))
    storage.addRequestResult(requestId = 3, mapOf(id(3) to link()))

    storage.retainRequests(listOf(2, 4))

    assertThat(storage.findHyperlink(id(1))).isNull()
    assertThat(storage.findHyperlink(id(2))).isNotNull()
    assertThat(storage.findHyperlink(id(3))).isNull()
  }

  @Test
  fun `a second result for the same request replaces the first`() {
    storage.addRequestResult(requestId = 1, mapOf(id(1) to link()))
    storage.addRequestResult(requestId = 1, mapOf(id(2) to link()))

    assertThat(storage.findHyperlink(id(1))).isNull()
    assertThat(storage.findHyperlink(id(2))).isNotNull()
  }

  private fun id(value: Long): TerminalHyperlinkId = TerminalHyperlinkId(value)

  private fun link(): HyperlinkInfo = HyperlinkInfo { }
}

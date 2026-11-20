// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(
  io.github.ketraterm.completion.api.ExperimentalTerminalCompletionApi::class,
  io.github.ketraterm.ui.swing.suggestion.ExperimentalShellSuggestionsApi::class,
)

package com.intellij.terminal.tests.reworked.frontend.ketraterm

import io.github.ketraterm.completion.api.TerminalCompletionEngines
import io.github.ketraterm.ui.swing.host.SwingCompletionSuggestionProvider
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestion
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionReplacement
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionRequest
import io.github.ketraterm.ui.swing.suggestion.replacementFor
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class KetraTermCompletionTest {
  @ParameterizedTest
  @ValueSource(strings = ["α", "é", "🙂", "👨‍👩‍👧‍👦"])
  fun `the library counts graphemes in the deletion span`(prefix: String) {
    val request = request("cat $prefix")
    val suggestion = SwingShellSuggestion("file", 4, request.cursorOffset, "test", "argument")
    assertThat(suggestion.replacementFor(request)).isEqualTo(SwingShellSuggestionReplacement(4, request.cursorOffset, "file", 1, 0))
  }

  @Test
  fun `the library rejects a replacement that splits a grapheme`() {
    val request = request("cat é")
    assertThat(SwingShellSuggestion("file", 5, 6, "test", "argument").replacementFor(request)).isNull()
  }

  @Test
  fun `the published completion engine supplies first token commands without repeating them`(): Unit = runBlocking {
    val provider = SwingCompletionSuggestionProvider(TerminalCompletionEngines.fromSources(emptyList()))
    assertThat(provider.suggestions(request("ca")).last().map { it.replacementText }).contains("cat")
    assertThat(provider.suggestions(request("cat")).last().map { it.replacementText }).doesNotContain("cat")
    assertThat(provider.suggestions(request("ca").copy(includeCommandNames = false)).last()).isEmpty()
    assertThat(provider.suggestions(request("git che")).last().map { it.replacementText }).contains("checkout")
  }

  private fun request(text: String): SwingShellSuggestionRequest = SwingShellSuggestionRequest(text, text.length)
}

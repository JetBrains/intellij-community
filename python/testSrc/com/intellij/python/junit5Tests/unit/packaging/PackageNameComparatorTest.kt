// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers [PyPackagingToolWindowService.createNameComparator] — the shared "exact, prefix, rest;
 * then downloads, length, name" sort used by the install dialog results list, the tool-window tree, and now the presenter
 * behind them. Pinning the ordering here catches regressions in the three call sites at once.
 */
internal class PackageNameComparatorTest {

  private fun sort(query: String, vararg names: String): List<String> = sortRanked(query, emptyMap(), *names)

  private fun sortRanked(query: String, downloads: Map<String, Int>, vararg names: String): List<String> {
    val c = PyPackagingToolWindowService.createNameComparator(query, downloads)
    return names.toList().sortedWith(compareBy(c) { it })
  }

  @Test
  fun `popular prefix match beats unranked prefix match of the same length`() {
    // The Slack report: "reques" listed requesck/requesst/request2 before requests.
    assertEquals(listOf("requests", "requesck", "requesst", "request2"),
                 sortRanked("reques", mapOf("requests" to 1000), "requesck", "requesst", "request2", "requests"))
  }

  @Test
  fun `popular prefix match beats shorter unranked prefix match`() {
    assertEquals(listOf("requests", "requer"),
                 sortRanked("reque", mapOf("requests" to 1000), "requer", "requests"))
  }

  @Test
  fun `exact match beats more popular prefix match`() {
    assertEquals(listOf("request", "requests"),
                 sortRanked("request", mapOf("requests" to 1000), "requests", "request"))
  }

  @Test
  fun `ranked substring match stays below unranked prefix match`() {
    assertEquals(listOf("django-a", "somedjango"),
                 sortRanked("django", mapOf("somedjango" to 1000), "somedjango", "django-a"))
  }

  @Test
  fun `more downloads win among ranked prefix matches`() {
    assertEquals(listOf("django-extensions", "django-cms"),
                 sortRanked("django-", mapOf("django-cms" to 10, "django-extensions" to 20), "django-cms", "django-extensions"))
  }

  @Test
  fun `downloads are looked up by normalized name`() {
    // Repositories may report un-normalized spellings; the ranking map is keyed by PyPackageName.normalizePackageName.
    assertEquals(listOf("typing_extensions", "typing-a"),
                 sortRanked("typing", mapOf("typing-extensions" to 1000), "typing-a", "typing_extensions"))
  }

  @Test
  fun `equal downloads fall back to length then name`() {
    assertEquals(listOf("abc-x", "abc-y", "abc-zz"),
                 sortRanked("abc", mapOf("abc-zz" to 5, "abc-y" to 5, "abc-x" to 5), "abc-zz", "abc-y", "abc-x"))
  }

  @Test
  fun `both start with prefix shortest wins`() {
    // "shortest wins" is the primary prefix rule in the comparator body.
    assertEquals(listOf("django", "django-cms", "djangorestframework"),
                 sort("django", "djangorestframework", "django-cms", "django"))
  }

  @Test
  fun `prefix beats substring even when substring name is shorter`() {
    // "django" prefixes django-a; "somedjango" merely contains it — prefix wins regardless of length.
    assertEquals(listOf("django-a", "somedjango", "unrelated"),
                 sort("django", "unrelated", "somedjango", "django-a"))
  }

  @Test
  fun `case-insensitive prefix match query is lowercased once and applied to lowercase names`() {
    // Names are already normalised (PyPackageName.normalize lowercases) — this test documents
    // that the comparator itself does the lowercase on the *query* side, so a mixed-case user
    // query still classifies prefix matches correctly.
    assertEquals(listOf("django", "django-cms"),
                 sort("Django", "django-cms", "django"))
  }

  @Test
  fun `no prefix matches falls back to lexicographic name order via thenBy`() {
    // Nothing starts with "zzz"; all rows land in the else -> 0 bucket, so .thenBy { it } sorts.
    assertEquals(listOf("alpha", "bravo", "charlie"),
                 sort("zzz", "charlie", "alpha", "bravo"))
  }

  @Test
  fun `empty query means every name is a prefix match shortest wins, then lex`() {
    // Every string starts with "" (the JLS-guaranteed prefix invariant), so the shortest-first
    // rule kicks in first; ties on length are broken by .thenBy { it }.
    assertEquals(listOf("ab", "cd", "abc"),
                 sort("", "abc", "cd", "ab"))
  }
}

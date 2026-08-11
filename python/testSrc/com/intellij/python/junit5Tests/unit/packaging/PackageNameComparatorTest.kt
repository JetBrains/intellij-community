// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers [PyPackagingToolWindowService.createNameComparator] — the shared "prefix first, then
 * name" sort used by the install dialog results list, the tool-window tree, and now the presenter
 * behind them. Pinning the ordering here catches regressions in the three call sites at once.
 */
internal class PackageNameComparatorTest {

  private fun sort(query: String, vararg names: String): List<String> {
    val c = PyPackagingToolWindowService.createNameComparator(query)
    return names.toList().sortedWith(compareBy(c) { it })
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

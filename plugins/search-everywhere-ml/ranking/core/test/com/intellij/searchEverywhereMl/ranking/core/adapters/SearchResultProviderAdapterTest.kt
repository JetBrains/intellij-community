package com.intellij.searchEverywhereMl.ranking.core.adapters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SearchResultProviderAdapterTest {
  @Test
  fun `string factory creates adapter with correct id`() {
    val adapter = SearchResultProviderAdapter.createAdapterFor("X")
    assertEquals("X", adapter.id)
  }

  @Test
  fun `string adapters with same id are equal`() {
    val a = SearchResultProviderAdapter.createAdapterFor("same")
    val b = SearchResultProviderAdapter.createAdapterFor("same")
    assertEquals(a, b)
  }

  @Test
  fun `string adapters with different ids are not equal`() {
    val a = SearchResultProviderAdapter.createAdapterFor("one")
    val b = SearchResultProviderAdapter.createAdapterFor("two")
    assertNotEquals(a, b)
  }
}

package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.searchEverywhereMl.ranking.core.MockSearchEverywhereContributor
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

  @Test
  fun `contributor factory uses searchProviderId`() {
    val contributor = MockSearchEverywhereContributor("myId")
    val adapter = SearchResultProviderAdapter.createAdapterFor(contributor)
    assertEquals("myId", adapter.id)
  }

  @Test
  fun `contributor and string adapters are equal when ids match`() {
    val contributor = MockSearchEverywhereContributor("shared")
    val contributorAdapter = SearchResultProviderAdapter.createAdapterFor(contributor)
    val stringAdapter = SearchResultProviderAdapter.createAdapterFor("shared")

    assertEquals(contributorAdapter, stringAdapter)
  }

  @Test
  fun `contributor and string adapters differ when ids differ`() {
    val contributor = MockSearchEverywhereContributor("alpha")
    val contributorAdapter = SearchResultProviderAdapter.createAdapterFor(contributor)
    val stringAdapter = SearchResultProviderAdapter.createAdapterFor("beta")

    assertNotEquals(contributorAdapter, stringAdapter)
  }

  @Test
  fun `LegacyContributorAdapter equals false for non-adapter`() {
    val contributor = MockSearchEverywhereContributor("id")
    val adapter = SearchResultProviderAdapter.createAdapterFor(contributor)

    @Suppress("AssertBetweenInconvertibleTypes")
    assertNotEquals(adapter, "not an adapter")
  }

  @Test
  fun `LegacyContributorAdapter hashCode based on id`() {
    val contributor = MockSearchEverywhereContributor("testId")
    val adapter = SearchResultProviderAdapter.createAdapterFor(contributor)

    assertEquals("testId".hashCode(), adapter.hashCode())
  }
}

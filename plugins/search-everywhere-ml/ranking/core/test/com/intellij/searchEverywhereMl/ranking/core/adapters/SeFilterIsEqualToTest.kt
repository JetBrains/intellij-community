package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilter
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SeFilterIsEqualToTest {
  @Nested
  inner class SeEverywhereFilterTests {
    @Test
    fun `same instance is equal`() {
      val filter = SeEverywhereFilter(true, true, listOf(SeProviderId("p1")))
      assertTrue(filter.isEqualTo(filter))
    }

    @Test
    fun `equal values are equal`() {
      val a = SeEverywhereFilter(true, false, listOf(SeProviderId("p1")))
      val b = SeEverywhereFilter(true, false, listOf(SeProviderId("p1")))
      assertTrue(a.isEqualTo(b))
    }

    @Test
    fun `different isAllTab is not equal`() {
      val a = SeEverywhereFilter(true, false, emptyList())
      val b = SeEverywhereFilter(false, false, emptyList())
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isEverywhere is not equal`() {
      val a = SeEverywhereFilter(true, true, emptyList())
      val b = SeEverywhereFilter(true, false, emptyList())
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different disabledProviderIds is not equal`() {
      val a = SeEverywhereFilter(true, true, listOf(SeProviderId("p1")))
      val b = SeEverywhereFilter(true, true, listOf(SeProviderId("p2")))
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different filter type is not equal`() {
      val everywhere = SeEverywhereFilter(true, true, emptyList())
      val targets = SeTargetsFilter(null, false, emptyList())
      assertFalse(everywhere.isEqualTo(targets))
    }
  }

  @Nested
  inner class SeTargetsFilterTests {
    @Test
    fun `same instance is equal`() {
      val filter = SeTargetsFilter("scope1", true, listOf("type1"))
      assertTrue(filter.isEqualTo(filter))
    }

    @Test
    fun `equal values are equal`() {
      val a = SeTargetsFilter("scope1", true, listOf("type1"))
      val b = SeTargetsFilter("scope1", true, listOf("type1"))
      assertTrue(a.isEqualTo(b))
    }

    @Test
    fun `different selectedScopeId is not equal`() {
      val a = SeTargetsFilter("scope1", true, emptyList())
      val b = SeTargetsFilter("scope2", true, emptyList())
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `null vs non-null selectedScopeId is not equal`() {
      val a = SeTargetsFilter(null, true, emptyList())
      val b = SeTargetsFilter("scope1", true, emptyList())
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isAutoTogglePossible is not equal`() {
      val a = SeTargetsFilter("scope1", true, emptyList())
      val b = SeTargetsFilter("scope1", false, emptyList())
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different hiddenTypes is not equal`() {
      val a = SeTargetsFilter("scope1", true, listOf("type1"))
      val b = SeTargetsFilter("scope1", true, listOf("type2"))
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different filter type is not equal`() {
      val targets = SeTargetsFilter("scope1", true, emptyList())
      val everywhere = SeEverywhereFilter(true, true, emptyList())
      assertFalse(targets.isEqualTo(everywhere))
    }
  }

  @Nested
  inner class SeTextFilterTests {
    @Test
    fun `same instance is equal`() {
      val filter = SeTextFilter("scope1", "type1", true, false, false)
      assertTrue(filter.isEqualTo(filter))
    }

    @Test
    fun `equal values are equal`() {
      val a = SeTextFilter("scope1", "type1", true, false, true)
      val b = SeTextFilter("scope1", "type1", true, false, true)
      assertTrue(a.isEqualTo(b))
    }

    @Test
    fun `different selectedScopeId is not equal`() {
      val a = SeTextFilter("scope1", null, false, false, false)
      val b = SeTextFilter("scope2", null, false, false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different selectedType is not equal`() {
      val a = SeTextFilter(null, "type1", false, false, false)
      val b = SeTextFilter(null, "type2", false, false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isCaseSensitive is not equal`() {
      val a = SeTextFilter(null, null, true, false, false)
      val b = SeTextFilter(null, null, false, false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isWholeWordsOnly is not equal`() {
      val a = SeTextFilter(null, null, false, true, false)
      val b = SeTextFilter(null, null, false, false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isRegex is not equal`() {
      val a = SeTextFilter(null, null, false, false, true)
      val b = SeTextFilter(null, null, false, false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different filter type is not equal`() {
      val text = SeTextFilter(null, null, false, false, false)
      val everywhere = SeEverywhereFilter(false, false, emptyList())
      assertFalse(text.isEqualTo(everywhere))
    }
  }

  @Nested
  inner class SeActionsFilterTests {
    @Test
    fun `same instance is equal`() {
      val filter = SeActionsFilter(true, false)
      assertTrue(filter.isEqualTo(filter))
    }

    @Test
    fun `equal values are equal`() {
      val a = SeActionsFilter(true, false)
      val b = SeActionsFilter(true, false)
      assertTrue(a.isEqualTo(b))
    }

    @Test
    fun `different includeDisabled is not equal`() {
      val a = SeActionsFilter(true, false)
      val b = SeActionsFilter(false, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different isAutoTogglePossible is not equal`() {
      val a = SeActionsFilter(true, true)
      val b = SeActionsFilter(true, false)
      assertFalse(a.isEqualTo(b))
    }

    @Test
    fun `different filter type is not equal`() {
      val actions = SeActionsFilter(true, false)
      val everywhere = SeEverywhereFilter(true, true, emptyList())
      assertFalse(actions.isEqualTo(everywhere))
    }
  }
}

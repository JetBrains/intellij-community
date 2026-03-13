package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.searchEverywhereMl.ranking.core.MockSearchEverywhereContributor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchResultAdapterTest {
  @Test
  fun `MlProbability toWeight converts 0 to 0`() {
    assertEquals(0, MlProbability(0.0).toWeight())
  }

  @Test
  fun `MlProbability toWeight converts 1 to 10000`() {
    assertEquals(10000, MlProbability(1.0).toWeight())
  }

  @Test
  fun `MlProbability toWeight converts 0_5 to 5000`() {
    assertEquals(5000, MlProbability(0.5).toWeight())
  }

  @Test
  fun `MlProbability toWeight truncates not rounds`() {
    assertEquals(1234, MlProbability(0.12349).toWeight())
  }

  @Test
  fun `MlProbability toWeight handles small values`() {
    assertEquals(1, MlProbability(0.0001).toWeight())
  }

  @Test
  fun `finalPriority uses legacy formula for non abbreviation`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 123, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)
    val processed = SearchResultAdapter.Processed(adapter, "element", null, null, MlProbability(0.2))

    assertEquals(200_000_123, processed.finalPriority)
  }

  @Test
  fun `finalPriority boosts action abbreviations to max ml weight`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 123, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)
    val abbreviationItem = createMatchedValue(GotoActionModel.MatchedValueType.ABBREVIATION)
    val processed = SearchResultAdapter.Processed(adapter, abbreviationItem, null, null, MlProbability(0.2))

    assertEquals(1_000_000_123, processed.finalPriority)
  }

  @Test
  fun `finalPriority falls back to original weight when mlProbability is absent`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 321, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)
    val processed = SearchResultAdapter.Processed(adapter, "element", null, null, null)

    assertEquals(321, processed.finalPriority)
  }

  @Test
  fun `StateLocalId preserves its value`() {
    val id = StateLocalId("test-id-123")
    assertEquals("test-id-123", id.value)
  }

  @Test
  fun `SessionWideId preserves its value`() {
    val id = SessionWideId(42)
    assertEquals(42, id.value)
  }

  @Test
  fun `legacy adapter has correct provider id`() {
    val contributor = MockSearchEverywhereContributor("myProviderId")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 100, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)

    assertEquals("myProviderId", adapter.provider.id)
  }

  @Test
  fun `legacy adapter has correct weight`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 42, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)

    assertEquals(42, adapter.originalWeight)
  }

  @Test
  fun `legacy adapter has correct stateLocalId`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("my-uuid-value", "element", 100, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)

    assertEquals("my-uuid-value", adapter.stateLocalId.value)
  }

  @Test
  fun `legacy adapter throws when uuid is null`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("element", 100, contributor)
    assertThrows(IllegalArgumentException::class.java) {
      SearchResultAdapter.createAdapterFor(info)
    }
  }

  @Test
  fun `legacy adapter fetchRawItemIfExists returns element`() {
    val element = "my-element-object"
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", element, 100, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)

    assertSame(element, adapter.fetchRawItemIfExists())
  }

  @Test
  fun `legacy adapter isSemantic is false for regular contributor`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 100, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(info)

    assertFalse(adapter.isSemantic)
  }

  @Test
  fun `Processed delegates provider to underlying adapter`() {
    val contributor = MockSearchEverywhereContributor("delegateId")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 100, contributor)
    val rawAdapter = SearchResultAdapter.createAdapterFor(info)
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals("delegateId", processed.provider.id)
  }

  @Test
  fun `Processed delegates originalWeight`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 77, contributor)
    val rawAdapter = SearchResultAdapter.createAdapterFor(info)
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals(77, processed.originalWeight)
  }

  @Test
  fun `Processed delegates stateLocalId`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-abc", "element", 100, contributor)
    val rawAdapter = SearchResultAdapter.createAdapterFor(info)
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals("uuid-abc", processed.stateLocalId.value)
  }

  @Test
  fun `Processed exposes its own fields`() {
    val contributor = MockSearchEverywhereContributor("id")
    val info = SearchEverywhereFoundElementInfo("uuid-1", "element", 100, contributor)
    val rawAdapter = SearchResultAdapter.createAdapterFor(info)
    val rawItem = "my-raw-item"
    val sessionWideId = SessionWideId(5)
    val mlFeatures = listOf<EventPair<*>>()
    val mlProbability = MlProbability(0.85)

    val processed = SearchResultAdapter.Processed(rawAdapter, rawItem, sessionWideId, mlFeatures, mlProbability)

    assertSame(rawItem, processed.rawItem)
    assertEquals(sessionWideId.value, processed.sessionWideId?.value)
    assertNotNull(processed.mlFeatures)
    assertTrue(processed.mlFeatures!!.isEmpty())
    assertEquals(mlProbability.value, processed.mlProbability?.value)
  }

  private fun createMatchedValue(type: GotoActionModel.MatchedValueType): GotoActionModel.MatchedValue {
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
      }
    }
    val wrapper = GotoActionModel.ActionWrapper(action, null, MatchMode.NAME, Presentation())
    return GotoActionModel.MatchedValue(wrapper, "q", type)
  }
}

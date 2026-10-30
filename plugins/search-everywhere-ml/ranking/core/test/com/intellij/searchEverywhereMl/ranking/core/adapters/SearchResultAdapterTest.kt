package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.searchEverywhereMl.ranking.core.TestItemDataFactory
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class SearchResultAdapterTest {
  private val itemDataFactory = TestItemDataFactory()

  @AfterEach
  fun tearDown() {
    itemDataFactory.close()
  }

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
    val adapter = createRawAdapter(originalWeight = 123)
    val processed = SearchResultAdapter.Processed(adapter, "element", null, null, MlProbability(0.2))

    assertEquals(200_000_123, processed.finalPriority)
  }

  @Test
  fun `finalPriority boosts action abbreviations to max ml weight`() {
    val adapter = createRawAdapter(originalWeight = 123)
    val abbreviationItem = createMatchedValue(GotoActionModel.MatchedValueType.ABBREVIATION)
    val processed = SearchResultAdapter.Processed(adapter, abbreviationItem, null, null, MlProbability(0.2))

    assertEquals(1_000_000_123, processed.finalPriority)
  }

  @Test
  fun `finalPriority falls back to original weight when mlProbability is absent`() {
    val adapter = createRawAdapter(originalWeight = 321)
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
  fun `item data adapter has correct provider id`() = runBlocking {
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create("element", "myProviderId"))

    assertEquals("myProviderId", adapter.provider.id)
  }

  @Test
  fun `item data adapter has correct weight`() = runBlocking {
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create("element", "id", weight = 42))

    assertEquals(42, adapter.originalWeight)
  }

  @Test
  fun `item data adapter has correct stateLocalId`() = runBlocking {
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create("element", "id", uuid = "my-uuid-value"))

    assertEquals("my-uuid-value", adapter.stateLocalId.value)
  }

  @Test
  fun `item data adapter fetchRawItemIfExists returns raw object`() = runBlocking {
    val element = "my-element-object"
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create(element, "id"))

    assertSame(element, adapter.fetchRawItemIfExists())
  }

  @Test
  fun `item data adapter fetchRawItemIfExists returns null after session is closed`() = runBlocking {
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create("element", "id"))
    itemDataFactory.close()

    assertNull(adapter.fetchRawItemIfExists())
  }

  @Test
  fun `item data adapter isSemantic is false for regular item`() = runBlocking {
    val adapter = SearchResultAdapter.createAdapterFor(itemDataFactory.create("element", "id"))

    assertFalse(adapter.isSemantic)
  }

  @Test
  fun `Processed delegates provider to underlying adapter`() {
    val rawAdapter = createRawAdapter(providerId = "delegateId")
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals("delegateId", processed.provider.id)
  }

  @Test
  fun `Processed delegates originalWeight`() {
    val rawAdapter = createRawAdapter(originalWeight = 77)
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals(77, processed.originalWeight)
  }

  @Test
  fun `Processed delegates stateLocalId`() {
    val rawAdapter = createRawAdapter(stateLocalId = "uuid-abc")
    val processed = SearchResultAdapter.Processed(rawAdapter, "element", null, null, null)

    assertEquals("uuid-abc", processed.stateLocalId.value)
  }

  @Test
  fun `Processed exposes its own fields`() {
    val rawAdapter = createRawAdapter()
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

  @Test
  fun `Processed delegates correction`() {
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.84)
    val adapter = TestSearchResultRawAdapter(
      provider = SearchResultProviderAdapter.createAdapterFor("actions"),
      originalWeight = 10,
      correction = correction,
      stateLocalId = StateLocalId("split-adapter-uuid"),
      rawItem = "raw-item",
    )
    val processed = SearchResultAdapter.Processed(adapter, adapter.fetchRawItemIfExists(), null, null, null)

    assertSame(correction, processed.correction)
  }

  private fun createRawAdapter(originalWeight: Int = 100, providerId: String = "id", stateLocalId: String = "uuid-1"): SearchResultAdapter.Raw {
    return TestSearchResultRawAdapter(
      provider = SearchResultProviderAdapter.createAdapterFor(providerId),
      originalWeight = originalWeight,
      stateLocalId = StateLocalId(stateLocalId),
      rawItem = "element",
    )
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

package com.intellij.codeInsight.completion.ml.performance.analysis.usage

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.turboComplete.NullableKindName
import com.intellij.turboComplete.analysis.usage.CombinedKindUsageStatistics
import com.intellij.turboComplete.analysis.usage.CombinedKindUsageTracker
import com.intellij.turboComplete.analysis.usage.KindStatistics
import com.intellij.turboComplete.analysis.usage.ValuePerPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class CombinedKindUsageTrackerTest {
  private enum class Event {
    CREATED,
    GENERATED_CORRECT,
    GENERATED_NOT_CORRECT,
  }

  private fun makeMockCompletionKind() = CompletionKind(
    NullableKindName.NONE_KIND,

    object : KindVariety {

      override fun kindsCorrespondToParameters(parameters: CompletionParameters) = true

      override val actualCompletionContributorClass: Class<*>
        get() = throw NotImplementedError()
    }
  )

  private fun getEventsStatistics(windowSize: Int, events: List<Event>): CombinedKindUsageStatistics {
    val kind = makeMockCompletionKind()
    val tracker = CombinedKindUsageTracker(kind, windowSize, windowSize)
    events.forEach {
      when (it) {
        Event.CREATED -> tracker.trackCreated()
        Event.GENERATED_CORRECT -> tracker.trackGenerated(true)
        Event.GENERATED_NOT_CORRECT -> tracker.trackGenerated(false)
      }
    }
    return tracker.getSummary()
  }

  private fun validateStates(windowSize: Int, events: List<Pair<Event, CombinedKindUsageStatistics>>) {
    val kind = makeMockCompletionKind()
    val tracker = CombinedKindUsageTracker(kind, windowSize, windowSize)
    events.forEach { (event, expectedStatistics) ->
      when (event) {
        Event.CREATED -> tracker.trackCreated()
        Event.GENERATED_CORRECT -> tracker.trackGenerated(true)
        Event.GENERATED_NOT_CORRECT -> tracker.trackGenerated(false)
      }
      assertEquals(expectedStatistics, tracker.getSummary())
    }
  }

  @Test
  fun `test total created`() {
    val windowSize = 3

    fun assertCreated(expected: Int, events: List<Event>) {
      assertEquals(expected, getEventsStatistics(windowSize, events).created)
    }

    assertCreated(0, listOf())
    assertCreated(1, listOf(Event.CREATED))
    assertCreated(2, listOf(Event.CREATED, Event.CREATED))
    assertCreated(15, List(15) { Event.CREATED })
    assertCreated(1, listOf(Event.CREATED, Event.GENERATED_CORRECT))
    assertCreated(1, listOf(Event.CREATED, Event.GENERATED_CORRECT, Event.GENERATED_NOT_CORRECT))
  }

  @Test
  fun `test total generated correct`() {
    val windowSize = 3

    fun assertCorrect(expected: Int, events: List<Event>) {
      assertEquals(expected, getEventsStatistics(windowSize, events).generated.correct)
    }

    assertCorrect(0, listOf())
    assertCorrect(0, listOf(Event.CREATED))
    assertCorrect(0, listOf(Event.GENERATED_NOT_CORRECT))
    assertCorrect(1, listOf(Event.GENERATED_CORRECT))
    assertCorrect(2, listOf(Event.GENERATED_NOT_CORRECT, Event.GENERATED_CORRECT, Event.CREATED, Event.CREATED, Event.GENERATED_CORRECT))
  }

  @Test
  fun `test total generated not correct`() {
    val windowSize = 3

    fun assertCorrect(expected: Int, events: List<Event>) {
      assertEquals(expected, getEventsStatistics(windowSize, events).generated.notCorrect)
    }

    assertCorrect(0, listOf())
    assertCorrect(0, listOf(Event.CREATED))
    assertCorrect(1, listOf(Event.GENERATED_NOT_CORRECT))
    assertCorrect(0, listOf(Event.GENERATED_CORRECT))
    assertCorrect(1, listOf(Event.GENERATED_NOT_CORRECT, Event.GENERATED_CORRECT, Event.CREATED, Event.CREATED, Event.GENERATED_CORRECT))
  }

  @Test
  fun `test total not correct in row`() {
    val windowSize = 3

    fun assertNotCorrectInRow(expected: Int, events: List<Event>) {
      assertEquals(expected, getEventsStatistics(windowSize, events).generatedInRow.notCorrect)
    }

    assertNotCorrectInRow(0, listOf())
    assertNotCorrectInRow(0, listOf(Event.CREATED))
    assertNotCorrectInRow(0, listOf(Event.GENERATED_CORRECT))
    assertNotCorrectInRow(1, listOf(Event.GENERATED_NOT_CORRECT))
    assertNotCorrectInRow(0, listOf(Event.GENERATED_NOT_CORRECT, Event.GENERATED_CORRECT, Event.CREATED, Event.CREATED,
                                    Event.GENERATED_CORRECT))
    assertNotCorrectInRow(3, List(4) { Event.GENERATED_NOT_CORRECT })
    assertNotCorrectInRow(2, List(2) { Event.GENERATED_NOT_CORRECT } + listOf(Event.CREATED))
    assertNotCorrectInRow(3, List(2) { Event.GENERATED_NOT_CORRECT } + listOf(Event.CREATED, Event.GENERATED_NOT_CORRECT))
    assertNotCorrectInRow(0, List(4) { Event.GENERATED_NOT_CORRECT } + listOf(Event.CREATED, Event.GENERATED_CORRECT))

    assertNotCorrectInRow(2, listOf(
      Event.CREATED,
      Event.CREATED,
      Event.GENERATED_CORRECT,
      Event.GENERATED_NOT_CORRECT,
      Event.CREATED,
      Event.GENERATED_NOT_CORRECT,
      Event.GENERATED_NOT_CORRECT,
      Event.GENERATED_CORRECT,
      Event.GENERATED_NOT_CORRECT,
      Event.GENERATED_NOT_CORRECT,
    ))
  }

  @Test
  fun `test recently generated correct`() {
    val windowSize = 2

    fun assertCreated(expected: Int, events: List<Event>) {
      assertEquals(expected, getEventsStatistics(windowSize, events).recentGenerated.value.correct)
    }

    assertCreated(0, listOf())
    assertCreated(0, listOf(Event.CREATED))
    assertCreated(1, listOf(Event.CREATED, Event.GENERATED_CORRECT))
    assertCreated(3, listOf(Event.CREATED, Event.GENERATED_CORRECT, Event.GENERATED_CORRECT, Event.GENERATED_CORRECT))
    assertCreated(0, listOf(Event.CREATED, Event.GENERATED_CORRECT, Event.GENERATED_CORRECT, Event.CREATED, Event.CREATED))
    assertCreated(0, listOf(Event.CREATED, Event.GENERATED_CORRECT, Event.GENERATED_CORRECT, Event.CREATED, Event.CREATED, Event.CREATED))
  }

  @Test
  fun `test event sequence`() {
    validateStates(2, listOf(
      Event.CREATED to CombinedKindUsageStatistics(
        1, generated = KindStatistics(0, 0), generatedInRow = KindStatistics(0, 0), ValuePerPeriod(1, KindStatistics(0, 0))
      ),
      Event.GENERATED_CORRECT to CombinedKindUsageStatistics(
        1, generated = KindStatistics(1, 0), generatedInRow = KindStatistics(1, 0), ValuePerPeriod(1, KindStatistics(1, 0))
      ),
      Event.CREATED to CombinedKindUsageStatistics(
        2, generated = KindStatistics(1, 0), generatedInRow = KindStatistics(1, 0), ValuePerPeriod(2, KindStatistics(1, 0))
      ),
      Event.GENERATED_CORRECT to CombinedKindUsageStatistics(
        2, generated = KindStatistics(2, 0), generatedInRow = KindStatistics(2, 0), ValuePerPeriod(2, KindStatistics(2, 0))
      ),
      Event.CREATED to CombinedKindUsageStatistics(
        3, generated = KindStatistics(2, 0), generatedInRow = KindStatistics(2, 0), ValuePerPeriod(2, KindStatistics(1, 0))
      ),
      Event.GENERATED_CORRECT to CombinedKindUsageStatistics(
        3, generated = KindStatistics(3, 0), generatedInRow = KindStatistics(2, 0), ValuePerPeriod(2, KindStatistics(2, 0))
      ),
      Event.CREATED to CombinedKindUsageStatistics(
        4, generated = KindStatistics(3, 0), generatedInRow = KindStatistics(2, 0), ValuePerPeriod(2, KindStatistics(1, 0))
      ),
      Event.GENERATED_NOT_CORRECT to CombinedKindUsageStatistics(
        4, generated = KindStatistics(3, 1), generatedInRow = KindStatistics(0, 1), ValuePerPeriod(2, KindStatistics(1, 1))
      ),
      Event.CREATED to CombinedKindUsageStatistics(
        5, generated = KindStatistics(3, 1), generatedInRow = KindStatistics(0, 1), ValuePerPeriod(2, KindStatistics(0, 1))
      ),
      Event.GENERATED_CORRECT to CombinedKindUsageStatistics(
        5, generated = KindStatistics(4, 1), generatedInRow = KindStatistics(1, 0), ValuePerPeriod(2, KindStatistics(1, 1))
      ),
      Event.CREATED to CombinedKindUsageStatistics(
        6, generated = KindStatistics(4, 1), generatedInRow = KindStatistics(1, 0), ValuePerPeriod(2, KindStatistics(1, 0))
      ),
      Event.GENERATED_CORRECT to CombinedKindUsageStatistics(
        6, generated = KindStatistics(5, 1), generatedInRow = KindStatistics(2, 0), ValuePerPeriod(2, KindStatistics(2, 0))
      ),
    ))
  }
}
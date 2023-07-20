package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail

internal interface FeaturesProviderTestCase {
  val provider: SearchEverywhereElementFeaturesProvider
  val testProject: Project

  fun roundDouble(value: Double) = SearchEverywhereElementFeaturesProvider.roundDouble(value)

  fun <T> checkThatFeature(feature: EventField<T>) = AssertionElementSelector(this, feature)

  fun checkThatFeatures() = AssertionElementSelector<List<*>>(this, null)

  /**
   * The [AssertionElementSelector] and its inner classes allow for writing concise
   * tests with methods chaining, that takes care of checking a feature
   *   - has a certain value (through [AssertionSpecifier.isEqualTo]), or
   *   - its value changes (through [AssertionSpecifier.changes]) upon some operation
   *
   * To use it, use either [checkThatFeature] to perform assertion on a single feature,
   * or [checkThatFeatures] to perform assertion on multiple features, passed as a
   * Map<String, *> to either [AssertionSpecifier.isEqualTo] or [AssertionSpecifier.changes]
   *
   * By default, all other arguments passed to [SearchEverywhereElementFeaturesProvider.getElementFeatures]
   * are equal to 0, apart from the element. To change that and pass a different value for testing, use:
   *   - [AssertionSpecifier.withCurrentTime]
   *   - [AssertionSpecifier.withQuery]
   *   - [AssertionSpecifier.withPriority]
   */
  class AssertionElementSelector<T>(private val testCase: FeaturesProviderTestCase, private val feature: EventField<T>?) {
    fun <E : Any> ofElement(element: E) = AssertionSpecifier(element)

    inner class AssertionSpecifier<E : Any>(private val element: E) {
      private var features: List<EventPair<*>> = emptyList()
      private var currentTime = 0L
      private var query = ""
      private var elementPriority = 0
      private val cache = FeaturesProviderCacheDataProvider().getDataToCache(testCase.testProject)

      /**
       * Specifies the current time / session start time that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withCurrentTime(time: Long): AssertionSpecifier<E> {
        currentTime = time
        return this
      }

      /**
       * Specifies the query length start time that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withQuery(query: String): AssertionSpecifier<E> {
        this.query = query
        return this
      }

      /**
       * Specifies the priority of the element that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withPriority(priority: Int): AssertionSpecifier<E> {
        elementPriority = priority
        return this
      }

      /**
       * Ensures, that features from [features] are not reported
       */
      fun withoutFeatures(features: List<EventField<*>>) = withoutMultipleFeatures(features)

      /**
       * Checks if the specified [feature] of the element has the [expectedValue]
       */
      fun isEqualTo(expectedValue: T) = assert(expectedValue)

      /**
       * Checks if the value of the [feature] will change from [from] to [to] after the specified operation is performed.
       *
       * @see ChangeOperation.after
       */
      fun changes(from: T, to: T) = ChangeOperation(from, to)

      /**
       * Checks whether feature exists or not
       */
      fun exists(expected: Boolean) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)
        if (feature == null) throw IllegalStateException("Cannot check if a feature exists with no feature specified")
        val containsFeature = feature in features.map { it.field }
        assertEquals(expected, containsFeature)
      }

      private fun withoutMultipleFeatures(expectedMissingFeatures: List<EventField<*>>) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)

        for (missingFeature in expectedMissingFeatures) {
          val actualFeatureByName = features.find { it.field == missingFeature }
          if (actualFeatureByName != null) {
            fail("Feature '${missingFeature.name}' should not be reported")
          }
        }
      }

      private fun assert(expectedValue: T?) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)

        if (features.isEmpty()) {
          val providerClass = testCase.provider::class.java.simpleName
          fail("The provider $providerClass has returned empty map of features. Maybe element type is incorrect?")
          return
        }

        @Suppress("UNCHECKED_CAST")
        when (feature) {
          null -> assertMultipleFeatures(expectedValue as List<EventPair<*>>)
          else -> assertSingleFeature(feature, expectedValue)
        }
      }

      private fun assertSingleFeature(feature: EventField<*>, expectedValue: Any?) {
        val actualValue = features.find { it.field == feature }
        assertEquals("Assertion of the feature ${feature.name} has failed", expectedValue, actualValue?.data)
      }

      private fun assertMultipleFeatures(expectedValue: List<EventPair<*>>) {
        for (expected in expectedValue) {
          assertSingleFeature(expected.field, expected.data)
        }
      }

      inner class ChangeOperation(private val oldValue: T?, private val newValue: T?) {
        /**
         * Checks if the value of the feature will change after [operation] is performed.
         */
        fun after(operation: (E) -> Unit) {
          assert(oldValue)
          operation(element)
          assert(newValue)
        }
      }
    }
  }
}


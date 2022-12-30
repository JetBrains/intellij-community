package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderTestCase.AssertionElementSelector.AssertionSpecifier
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail

internal interface FeaturesProviderTestCase {
  val provider: SearchEverywhereElementFeaturesProvider
  val testProject: Project

  fun roundDouble(value: Double) = SearchEverywhereElementFeaturesProvider.roundDouble(value)

  fun checkThatFeature(feature: String) = AssertionElementSelector(this, feature)

  fun checkThatFeatures() = AssertionElementSelector(this, null)

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
  class AssertionElementSelector(private val testCase: FeaturesProviderTestCase, private val feature: String?) {
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
      fun isEqualTo(expectedValue: Any?) = assert(expectedValue)

      /**
       * Checks if the value of the [feature] will change from [from] to [to] after the specified operation is performed.
       *
       * @see ChangeOperation.after
       */
      fun changes(from: Any?, to: Any?) = ChangeOperation(from, to)

      /**
       * Checks whether feature exists or not
       */
      fun exists(expected: Boolean) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)
        val containsFeature = feature in features.map { it.field.name }
        assertEquals(expected, containsFeature)
      }

      private fun withoutMultipleFeatures(expectedMissingFeatures: List<EventField<*>>) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)

        for (missingFeature in expectedMissingFeatures) {
          val featureName = missingFeature.name
          val actualFeatureByName = features.find { it.field.name == featureName }
          if (actualFeatureByName != null) {
            fail("Feature '$featureName' should not be reported")
          }
        }
      }

      private fun assert(expectedValue: Any?) {
        features = testCase.provider.getElementFeatures(element, currentTime, query, elementPriority, cache)

        if (features.isEmpty()) {
          val providerClass = testCase.provider::class.java.simpleName
          fail("The provider $providerClass has returned empty map of features. Maybe element type is incorrect?")
          return
        }

        when (feature) {
          null -> assertMultipleFeatures(expectedValue)
          else -> assertSingleFeature(feature, expectedValue)
        }
      }

      private fun assertSingleFeature(key: String, expectedValue: Any?) {
        val actualValue = features.find { it.field.name == key }
        assertEquals("Assertion of the feature $key has failed", expectedValue, actualValue?.data)
      }

      private fun assertMultipleFeatures(expectedValue: Any?) {
        if (expectedValue == null) {
          throw IllegalArgumentException("The expected value for checkThatFeatures() must be of type List<EventPair<*>>, not null")
        }
        else if (expectedValue !is List<*>) {
          throw IllegalArgumentException("The expected value for checkThatFeatures() must be of type List<EventPair<*>>")
        }

        for (expected in expectedValue) {
          if (expected !is EventPair<*>) {
            throw IllegalArgumentException("The expected value for checkThatFeatures() must be of type List<EventPair<*>>")
          }

          assertSingleFeature(expected.field.name, expected.data)
        }
      }

      inner class ChangeOperation(private val oldValue: Any?, private val newValue: Any?) {
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


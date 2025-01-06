// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.*
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.experiments.ExperimentStatus
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.lang.java.JavaLanguage
import com.intellij.mocks.TestExperimentStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.completion.CompletionStatsPolicy
import com.intellij.stats.completion.events.CompletionStartedEvent
import com.intellij.testFramework.replaceService
import junit.framework.TestCase

private typealias Before = () -> Unit
private typealias After = () -> Unit

class MLFeaturesComputingTest : CompletionLoggingTestBase() {
  private val contextFeatureProvider = TestContextFeatureProvider()
  private val elementFeatureProvider = TestElementFeatureProvider()

  fun `test features should be calculated and logged if logging enabled`() = doTest(
    before(enableLogging = true, enableRanking = false, experimentWithoutComputingFeatures = false, experimentWithoutLoggingFeatures = false),
    after(shouldComputeFeatures = true, shouldLogElementFeatures = true))

  fun `test features should be calculated but not logged if ranking enabled`() = doTest(
    before(enableLogging = false, enableRanking = true, experimentWithoutComputingFeatures = false, experimentWithoutLoggingFeatures = false),
    after(shouldComputeFeatures = true, shouldLogElementFeatures = false))

  fun `test features should be calculated and logged if ranking and logging enabled`() = doTest(
    before(enableLogging = true, enableRanking = true, experimentWithoutComputingFeatures = false, experimentWithoutLoggingFeatures = false),
    after(shouldComputeFeatures = true, shouldLogElementFeatures = true))

  fun `test features should not be calculated and logged if ranking and logging disabled`() = doTest(
    before(enableLogging = false, enableRanking = false, experimentWithoutComputingFeatures = false, experimentWithoutLoggingFeatures = false),
    after(shouldComputeFeatures = false, shouldLogElementFeatures = false))

  fun `test features should not be calculated and logged if in such experiment group`() = doTest(
    before(enableLogging = true, enableRanking = false, experimentWithoutComputingFeatures = true, experimentWithoutLoggingFeatures = false),
    after(shouldComputeFeatures = false, shouldLogElementFeatures = false))

  fun `test element features should not be logged but calculated if in such experiment group`() = doTest(
    before(enableLogging = true, enableRanking = false, experimentWithoutComputingFeatures = false, experimentWithoutLoggingFeatures = true),
    after(shouldComputeFeatures = true, shouldLogElementFeatures = false))

  private fun doTest(before: Before, after: After) {
    ApplicationManager.getApplication().replaceService(ExperimentStatus::class.java, TestExperimentStatus(), testRootDisposable)
    ContextFeatureProvider.EP_NAME.addExplicitExtension(JavaLanguage.INSTANCE, contextFeatureProvider, testRootDisposable)
    ElementFeatureProvider.EP_NAME.addExplicitExtension(JavaLanguage.INSTANCE, elementFeatureProvider, testRootDisposable)
    MutableLookupStorage.setComputeFeaturesAlways(false, testRootDisposable)

    before()
    myFixture.completeBasic()
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    after()
  }

  private fun before(enableLogging: Boolean,
                     enableRanking: Boolean,
                     experimentWithoutComputingFeatures: Boolean,
                     experimentWithoutLoggingFeatures: Boolean): Before {
    return {
      setLoggingEnabled(enableLogging)
      setRankingEnabled(enableRanking)
      setExperiment(experimentWithoutComputingFeatures, experimentWithoutLoggingFeatures)
    }
  }

  private fun after(shouldComputeFeatures: Boolean, shouldLogElementFeatures: Boolean): After {
    return {
      TestCase.assertEquals(shouldComputeFeatures, contextFeatureProvider.invocationCount != 0)
      if (shouldComputeFeatures) {
        TestCase.assertEquals("Context features should be calculated exactly once", 1, contextFeatureProvider.invocationCount)
      }
      TestCase.assertEquals(shouldComputeFeatures, elementFeatureProvider.invocationCount != 0)
      if (trackedEvents.isEmpty()) {
        TestCase.assertTrue(!shouldLogElementFeatures)
      }
      else {
        val startedEvent = trackedEvents.first() as CompletionStartedEvent
        val firstItem = startedEvent.newCompletionListItems[0]
        val relevance = firstItem.relevance!!
        TestCase.assertEquals(shouldLogElementFeatures, relevance.containsKey(TestElementFeatureProvider.FULL_FEATURE_NAME))
      }
    }
  }

  private fun setLoggingEnabled(value: Boolean) {
    if (!value) {
      CompletionStatsPolicy.Instance.addExplicitExtension(JavaLanguage.INSTANCE, object : CompletionStatsPolicy {
        override fun isStatsLogDisabled(): Boolean = true
      }, testRootDisposable)
    }
  }

  private fun setRankingEnabled(value: Boolean) {
    if (value) {
      RankingSupport.enableInTests(testRootDisposable)
    }
  }

  private fun setExperiment(experimentWithoutComputingFeatures: Boolean, experimentWithoutLoggingFeatures: Boolean) {
    if (experimentWithoutComputingFeatures || experimentWithoutLoggingFeatures) {
      val experimentStatus = ExperimentStatus.getInstance() as TestExperimentStatus
      experimentStatus.updateExperimentSettings(inExperiment = true,
                                                shouldRank = false,
                                                shouldShowArrows = false,
                                                shouldCalculateFeatures = !experimentWithoutComputingFeatures,
                                                shouldLogElementFeatures = !experimentWithoutLoggingFeatures)
    }
  }

  private class TestContextFeatureProvider : ContextFeatureProvider {
    @Volatile
    var invocationCount = 0

    override fun getName(): String = "test"

    override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
      invocationCount += 1
      return emptyMap()
    }
  }

  private class TestElementFeatureProvider() : ElementFeatureProvider {
    override fun getName(): String = "test"

    @Volatile
    var invocationCount = 0

    override fun calculateFeatures(element: LookupElement,
                                   location: CompletionLocation,
                                   contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
      invocationCount += 1
      return mapOf(FEATURE_NAME to MLFeatureValue.binary(true))
    }

    companion object {
      private const val FEATURE_NAME = "feature_exists"
      const val FULL_FEATURE_NAME = "ml_test_feature_exists"
    }
  }
}

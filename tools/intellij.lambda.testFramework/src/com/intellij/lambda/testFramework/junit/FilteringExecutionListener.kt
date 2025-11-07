package com.intellij.lambda.testFramework.junit

import org.junit.platform.engine.*
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Listener that filters test execution based on post-discovery filters
 */
class FilteringExecutionListener(
  private val delegate: EngineExecutionListener,
  private val postDiscoveryFilters: List<PostDiscoveryFilter>,
) : EngineExecutionListener {

  private val composedFilter = Filter.composeFilters(postDiscoveryFilters)
  private val skippedDescriptors = mutableSetOf<UniqueId>()

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    // Check if this test should be filtered out
    if (shouldFilter(testDescriptor)) {
      skippedDescriptors.add(testDescriptor.uniqueId)
      // Don't start execution for filtered tests - just skip them silently
      return
    }
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    // Only finish if we actually started it
    if (!skippedDescriptors.contains(testDescriptor.uniqueId)) {
      delegate.executionFinished(testDescriptor, testExecutionResult)
    }
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  private fun shouldFilter(testDescriptor: TestDescriptor): Boolean {
    if (postDiscoveryFilters.isEmpty()) return false

    // Don't filter containers, only tests
    if (!testDescriptor.isTest) return false

    val filterResult = composedFilter.apply(testDescriptor)
    return filterResult.excluded()
  }
}
package com.intellij.lambda.testFramework.junit

import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.engine.*
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Listener that filters test execution based on post-discovery filters
 */
class FilteringExecutionListener(
  private val delegate: EngineExecutionListener,
  private val postDiscoveryFilters: List<PostDiscoveryFilter>,
  private val currentMode: IdeRunMode,
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
    if (postDiscoveryFilters.isNotEmpty() && testDescriptor.isTest) {
      val filterResult = composedFilter.apply(testDescriptor)
      if (filterResult.excluded()) return true
    }

    return shouldFilterByMode(testDescriptor)
  }

  private fun shouldFilterByMode(testDescriptor: TestDescriptor): Boolean {
    if (!testDescriptor.isTest) return false

    val source = testDescriptor.source.orElse(null) ?: return false

    val testClass = when (source) {
      is MethodSource -> {
        try {
          Class.forName(source.className)
        }
        catch (e: ClassNotFoundException) {
          logOutput("[FilteringExecutionListener]: Could not load class ${source.className}")
          return false
        }
      }
      is ClassSource -> {
        try {
          Class.forName(source.className)
        }
        catch (e: ClassNotFoundException) {
          logOutput("[FilteringExecutionListener]: Could not load class ${source.className}")
          return false
        }
      }
      else -> return false
    }

    val testMethod = if (source is MethodSource) {
      try {
        source.javaMethod
      }
      catch (e: NoSuchMethodException) {
        null
      }
    }
    else null

    // Check method-level annotation first, then class-level
    val allowedModes = getModesToRun(testMethod).ifEmpty { getModesToRun(testClass) }
    val isModeAllowed = currentMode in allowedModes

    if (!isModeAllowed) {
      logOutput("[FilteringExecutionListener]: Filtering out test ${testDescriptor.displayName} because it should run in $allowedModes but current mode is $currentMode")
    }

    return !isModeAllowed
  }
}
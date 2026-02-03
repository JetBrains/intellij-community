package com.intellij.lambda.testFramework.junit

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.containers.orNull
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Filters out classes with [RunInMonolithAndSplitMode] annotation from Jupiter engine execution.
 * These classes will only be executed by [GroupByModeTestEngine].
 */
class GroupByModePostDiscoveryFilter : PostDiscoveryFilter {
  override fun apply(testDescriptor: TestDescriptor): FilterResult {
    val engineId = testDescriptor.uniqueId.engineId.orNull()

    if (!isGroupedExecutionEnabled) {
      thisLogger().info("Test descriptor is included in all JUnit engines because grouped execution is disabled")
      return FilterResult.included("No filter is applied")
    }

    if (engineId != GroupByModeTestEngine.ENGINE_ID) {
      return FilterResult.excluded("Not a ${GroupByModeTestEngine.engineClassName} test")
    }

    return FilterResult.included("Test is handled by ${GroupByModeTestEngine.engineClassName} engine")
  }
}
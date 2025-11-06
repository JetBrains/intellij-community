package com.intellij.lambda.testFramework.junit

import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.containers.orNull
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Filters out classes with [ExecuteInMonolithAndSplitMode] annotation from Jupiter engine execution.
 * These classes will only be executed by [GroupByModeTestEngine].
 */
class GroupByModePostDiscoveryFilter : PostDiscoveryFilter {
  companion object {
    private val className = GroupByModePostDiscoveryFilter::class.simpleName
    private fun log(message: String) = logOutput("[$className]: $message")
  }

  override fun apply(testDescriptor: TestDescriptor): FilterResult {
    val engineId = testDescriptor.uniqueId.engineId.orNull()

    if (!isGroupedExecutionEnabled) {
      log("Test descriptor is included in all JUnit engines because grouped execution is disabled")
      return FilterResult.included("No filter is applied")
    }

    if (engineId != GroupByModeTestEngine.ENGINE_ID) {
      return FilterResult.excluded("Not a ${GroupByModeTestEngine.engineClassName} test")
    }

    return FilterResult.included("Test is handled by ${GroupByModeTestEngine.engineClassName} engine")
  }
}
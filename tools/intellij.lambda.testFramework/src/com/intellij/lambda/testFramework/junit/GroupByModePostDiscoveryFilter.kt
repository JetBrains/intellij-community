package com.intellij.lambda.testFramework.junit

import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
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
    // Only filter Jupiter engine's tests
    val engineId = testDescriptor.uniqueId.engineId.orElse(null)
    if (engineId != "junit-jupiter") {
      return FilterResult.included("Not a Jupiter test")
    }

    // Check if this test belongs to a @GroupTestsByMode class
    val source = testDescriptor.source.orElse(null)
    if (source is ClassSource) {
      try {
        val clazz = Class.forName(source.className)
        if (clazz.isAnnotationPresent(ExecuteInMonolithAndSplitMode::class.java)) {
          log("Excluding ${source.className} from Jupiter")
          return FilterResult.excluded("Managed by ${GroupByModeTestEngine::class.simpleName}")
        }
      }
      catch (e: ClassNotFoundException) {
        // Continue
      }
    }

    // Also check parent descriptors
    var parent = testDescriptor.parent.orElse(null)
    while (parent != null) {
      val parentSource = parent.source.orElse(null)
      if (parentSource is ClassSource) {
        try {
          val clazz = Class.forName(parentSource.className)
          if (clazz.isAnnotationPresent(ExecuteInMonolithAndSplitMode::class.java)) {
            log("Excluding ${testDescriptor.displayName} (parent has @${ExecuteInMonolithAndSplitMode::class.simpleName})")
            return FilterResult.excluded("Parent managed by ${GroupByModeTestEngine::class.simpleName}")
          }
        }
        catch (e: ClassNotFoundException) {
          // Continue
        }
      }
      parent = parent.parent.orElse(null)
    }

    return FilterResult.included("Not a @${ExecuteInMonolithAndSplitMode::class.simpleName} test")
  }
}
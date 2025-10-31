package com.intellij.lambda.testFramework.junit

import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Filters out @GroupTestsByMode classes from Jupiter engine execution.
 * These classes will only be executed by GroupByModeTestEngine.
 */
class GroupByModePostDiscoveryFilter : PostDiscoveryFilter {

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
        if (clazz.isAnnotationPresent(GroupTestsByMode::class.java)) {
          println("GroupByModePostDiscoveryFilter: Excluding ${source.className} from Jupiter")
          return FilterResult.excluded("Managed by GroupByModeTestEngine")
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
          if (clazz.isAnnotationPresent(GroupTestsByMode::class.java)) {
            println("GroupByModePostDiscoveryFilter: Excluding ${testDescriptor.displayName} (parent has @GroupTestsByMode)")
            return FilterResult.excluded("Parent managed by GroupByModeTestEngine")
          }
        }
        catch (e: ClassNotFoundException) {
          // Continue
        }
      }
      parent = parent.parent.orElse(null)
    }

    return FilterResult.included("Not a GroupTestsByMode test")
  }
}
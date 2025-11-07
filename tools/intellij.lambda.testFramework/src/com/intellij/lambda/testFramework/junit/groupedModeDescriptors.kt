package com.intellij.lambda.testFramework.junit

import org.junit.platform.engine.ConfigurationParameters
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Custom engine descriptor that stores configuration for grouped execution
 */
class GroupedModeEngineDescriptor(
  uniqueId: UniqueId,
  val classSelectors: List<ClassSelector>,
  val configurationParameters: ConfigurationParameters,
  val postDiscoveryFilters: List<PostDiscoveryFilter>,
) : EngineDescriptor(uniqueId, "Group by Mode")

/**
 * Descriptor for a class that will be executed in grouped mode
 */
class GroupedClassDescriptor(
  uniqueId: UniqueId,
  private val className: String,
  classSource: TestSource?,
) : AbstractTestDescriptor(
  uniqueId,
  className,
  classSource ?: ClassSource.from(className)
) {

  override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER

  override fun mayRegisterTests(): Boolean = true
}

/**
 * Descriptor for a mode container (MONOLITH or SPLIT)
 */
class ModeContainerDescriptor(
  uniqueId: UniqueId,
  val mode: IdeRunMode,
  val className: String,
) : AbstractTestDescriptor(uniqueId, "[$mode]") {

  override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER

  override fun mayRegisterTests(): Boolean = true
}

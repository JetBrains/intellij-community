package com.intellij.lambda.testFramework.junit

import com.intellij.util.containers.orNull
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource

/**
 * Listener that translates Jupiter execution events to our custom descriptors
 */
class TranslatingExecutionListener(
  private val delegate: EngineExecutionListener,
  private val modeContainer: ModeContainerDescriptor,
) : EngineExecutionListener {

  private val descriptorMap = mutableMapOf<UniqueId, TestDescriptor>()
  private val startedDescriptors = mutableSetOf<UniqueId>()

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    if (!shouldInclude(testDescriptor)) return

    val synthetic = createSyntheticDescriptor(testDescriptor)
    descriptorMap[testDescriptor.uniqueId] = synthetic
    delegate.dynamicTestRegistered(synthetic)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) {
    if (!shouldInclude(testDescriptor)) return

    // Only report skipped if the descriptor was already registered via dynamicTestRegistered or executionStarted
    val synthetic = descriptorMap[testDescriptor.uniqueId]
    if (synthetic != null && synthetic.parent.isPresent) {
      delegate.executionSkipped(synthetic, reason)
    }
    // If the descriptor doesn't exist in our map or has no parent,
    // it means it was filtered out and never registered - silently ignore it
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    // Skip engine and class containers from Jupiter
    if (testDescriptor.type == TestDescriptor.Type.CONTAINER &&
        (testDescriptor.uniqueId.engineId.orNull() == JUPITER_ENGINE_ID ||
         testDescriptor.source.orNull() is ClassSource)) {
      return
    }

    if (!shouldInclude(testDescriptor)) return

    val synthetic = getOrCreateSyntheticDescriptor(testDescriptor)
    startedDescriptors.add(synthetic.uniqueId)
    startIde()
    delegate.executionStarted(synthetic)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    // Skip engine and class containers from Jupiter
    if (testDescriptor.type == TestDescriptor.Type.CONTAINER &&
        (testDescriptor.uniqueId.engineId.orNull() == JUPITER_ENGINE_ID ||
         testDescriptor.source.orNull() is ClassSource)) {
      return
    }

    val synthetic = descriptorMap[testDescriptor.uniqueId]
    if (synthetic != null && startedDescriptors.contains(synthetic.uniqueId)) {
      delegate.executionFinished(synthetic, testExecutionResult)
    }
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    val synthetic = descriptorMap[testDescriptor.uniqueId]
    if (synthetic != null) {
      delegate.reportingEntryPublished(synthetic, entry)
    }
  }

  private fun shouldInclude(descriptor: TestDescriptor): Boolean {
    if (!descriptor.isTest) return true

    val displayName = descriptor.displayName
    return when {
      displayName.contains(modeContainer.mode.name) -> true
      IdeRunMode.entries.map { it.name }.any { displayName.contains(it) } -> false
      else -> true
    }
  }

  private fun getOrCreateSyntheticDescriptor(jupiterDescriptor: TestDescriptor): TestDescriptor {
    return descriptorMap.getOrPut(jupiterDescriptor.uniqueId) {
      createSyntheticDescriptor(jupiterDescriptor)
    }
  }

  private fun createSyntheticDescriptor(jupiterDescriptor: TestDescriptor): TestDescriptor {
    // Extract just the method name from the chain of execution
    val testMethodName = jupiterDescriptor.uniqueId.segments[2].value
    val cleanedDisplayName = when {
      // If it already has the mode prefix, keep it
      testMethodName.startsWith("[") -> testMethodName
      // Otherwise, extract the method name (remove parameters if present)
      else -> testMethodName.substringBefore("(").takeIf { it.isNotEmpty() } ?: testMethodName
    }

    val syntheticId = modeContainer.uniqueId.append("test", jupiterDescriptor.uniqueId.toString())

    val synthetic = object : AbstractTestDescriptor(
      syntheticId,
      cleanedDisplayName,  // Use the cleaned display name
      jupiterDescriptor.source.orElse(null)
    ) {
      override fun getType(): TestDescriptor.Type = jupiterDescriptor.type
      override fun mayRegisterTests(): Boolean = jupiterDescriptor.mayRegisterTests()
    }

    modeContainer.addChild(synthetic)
    return synthetic
  }

  private fun startIde() {
    IdeInstance.startIde(modeContainer.mode)
  }
}
package com.intellij.lambda.testFramework.junit

import org.junit.platform.engine.*
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Filter that excludes tests with @GroupTestsByMode from Jupiter engine.
 * These tests will be handled by GroupByModeTestEngine instead.
 */
class GroupTestsByModeFilter : PostDiscoveryFilter {

  override fun apply(descriptor: TestDescriptor): FilterResult {
    // Check if this descriptor or its parent has @GroupTestsByMode
    if (hasGroupTestsByModeAnnotation(descriptor)) {
      // Exclude from Jupiter - will be handled by GroupByModeTestEngine
      return FilterResult.excluded("Handled by GroupByModeTestEngine")
    }

    // Include everything else
    return FilterResult.included("Not grouped by mode")
  }

  private fun hasGroupTestsByModeAnnotation(descriptor: TestDescriptor): Boolean {
    // Check current descriptor
    val source = descriptor.source.orElse(null)
    if (source is ClassSource) {
      try {
        val clazz = Class.forName(source.className)
        if (clazz.isAnnotationPresent(GroupTestsByMode::class.java)) {
          return true
        }
      } catch (e: ClassNotFoundException) {
        // Continue
      }
    }

    // Check parent (method's class might have the annotation)
    val parent = descriptor.parent.orElse(null)
    if (parent != null && parent != descriptor) {
      return hasGroupTestsByModeAnnotation(parent)
    }

    return false
  }
}

/**
 * Custom TestEngine that wraps JUnit Jupiter and enables grouped-by-mode test execution.
 *
 * When a test class is annotated with @GroupTestsByMode, this engine ensures
 * tests execute in two phases:
 * 1. All MONOLITH mode tests across all methods
 * 2. All SPLIT mode tests across all methods
 * 
 * For tests without @GroupTestsByMode, this engine returns an empty descriptor,
 * allowing Jupiter to handle them normally.
 */
class GroupByModeTestEngine : TestEngine {

  companion object {
    const val ENGINE_ID = "group-by-mode"
    private const val JUPITER_ENGINE_ID = "junit-jupiter"
  }

  private val jupiterEngine: TestEngine by lazy {
    java.util.ServiceLoader.load(TestEngine::class.java)
      .find { it.id == JUPITER_ENGINE_ID }
    ?: error("JUnit Jupiter engine not found")
  }

  override fun getId(): String = ENGINE_ID

  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
    // First, let Jupiter discover tests
    val jupiterUniqueId = UniqueId.forEngine(JUPITER_ENGINE_ID)
    val jupiterDescriptor = jupiterEngine.discover(discoveryRequest, jupiterUniqueId)

    // Check if any discovered tests need grouped execution
    if (needsGroupedExecution(jupiterDescriptor)) {
      // Create our wrapper descriptor with all Jupiter's tests
      val groupedDescriptor = GroupedModeEngineDescriptor(uniqueId)
      jupiterDescriptor.children.forEach { child ->
        groupedDescriptor.addChild(child)
      }
      groupedDescriptor.jupiterDescriptor = jupiterDescriptor
      return groupedDescriptor
    }

    // No grouped execution needed, return empty descriptor so Jupiter handles it
    return EngineDescriptor(uniqueId, "Group by Mode (inactive)")
  }

  override fun execute(executionRequest: ExecutionRequest) {
    val rootDescriptor = executionRequest.rootTestDescriptor

    if (rootDescriptor is GroupedModeEngineDescriptor && rootDescriptor.children.isNotEmpty()) {
      executeGroupedMode(rootDescriptor, executionRequest)
    }
    // If not our descriptor or empty, do nothing (Jupiter will handle it)
  }

  private fun needsGroupedExecution(descriptor: TestDescriptor): Boolean {
    return hasGroupTestsByModeAnnotation(descriptor)
  }

  private fun hasGroupTestsByModeAnnotation(descriptor: TestDescriptor): Boolean {
    // Check current descriptor
    val source = descriptor.source.orElse(null)
    if (source is ClassSource) {
      try {
        val clazz = Class.forName(source.className)
        if (clazz.isAnnotationPresent(GroupTestsByMode::class.java)) {
          return true
        }
      }
      catch (e: ClassNotFoundException) {
        // Continue checking children
      }
    }

    // Check children recursively
    return descriptor.children.any { hasGroupTestsByModeAnnotation(it) }
  }

  private fun executeGroupedMode(
    groupedDescriptor: GroupedModeEngineDescriptor,
    executionRequest: ExecutionRequest,
  ) {
    val listener = executionRequest.engineExecutionListener

    listener.executionStarted(groupedDescriptor)

    try {
      // Phase 1: Execute MONOLITH tests
      println("##teamcity[message text='=== Executing ${IdeRunMode.MONOLITH} mode tests ===' status='NORMAL']")
      executeFilteredMode(groupedDescriptor.jupiterDescriptor!!, executionRequest, IdeRunMode.MONOLITH)

      // Phase 2: Execute SPLIT tests
      println("##teamcity[message text='=== Executing ${IdeRunMode.SPLIT} mode tests ===' status='NORMAL']")
      executeFilteredMode(groupedDescriptor.jupiterDescriptor!!, executionRequest, IdeRunMode.SPLIT)

      listener.executionFinished(groupedDescriptor, TestExecutionResult.successful())
    }
    catch (e: Exception) {
      listener.executionFinished(groupedDescriptor, TestExecutionResult.failed(e))
    }
  }

  private fun executeFilteredMode(
    jupiterDescriptor: TestDescriptor,
    originalRequest: ExecutionRequest,
    targetMode: IdeRunMode,
  ) {
    val filteredListener = ModeFilteringExecutionListener(
      originalRequest.engineExecutionListener,
      targetMode
    )

    // Try to create request using the factory method with all parameters
    try {
      val outputDirectoryProvider = originalRequest.outputDirectoryProvider
      val store = originalRequest.store
      
      // Use the static factory method
      val filteredRequest = ExecutionRequest.create(
        jupiterDescriptor,
        filteredListener,
        originalRequest.configurationParameters,
        outputDirectoryProvider,
        store
      )
      jupiterEngine.execute(filteredRequest)
    }
    catch (e: Exception) {
      // Fall back to deprecated 3-parameter factory if the store/outputDir are not available
      val filteredRequest = ExecutionRequest.create(
        jupiterDescriptor,
        filteredListener,
        originalRequest.configurationParameters
      )
      jupiterEngine.execute(filteredRequest)
    }
  }
}

/**
 * Custom descriptor that wraps Jupiter's engine descriptor for grouped execution.
 */
class GroupedModeEngineDescriptor(
  uniqueId: UniqueId,
) : EngineDescriptor(uniqueId, "Group by Mode") {

  var jupiterDescriptor: TestDescriptor? = null
}

/**
 * Execution listener that filters test execution based on the target mode.
 */
class ModeFilteringExecutionListener(
  private val delegate: EngineExecutionListener,
  private val targetMode: IdeRunMode,
) : EngineExecutionListener {

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    if (shouldInclude(testDescriptor)) {
      delegate.dynamicTestRegistered(testDescriptor)
    }
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) {
    if (shouldInclude(testDescriptor)) {
      delegate.executionSkipped(testDescriptor, reason)
    }
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    if (shouldInclude(testDescriptor)) {
      delegate.executionStarted(testDescriptor)
    }
    // Don't report skipped tests to avoid noise
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    if (shouldInclude(testDescriptor)) {
      delegate.executionFinished(testDescriptor, testExecutionResult)
    }
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    if (shouldInclude(testDescriptor)) {
      delegate.reportingEntryPublished(testDescriptor, entry)
    }
  }

  private fun shouldInclude(descriptor: TestDescriptor): Boolean {
    // Always include containers (engines, classes) to allow proper hierarchy
    if (!descriptor.isTest) {
      return true
    }

    val displayName = descriptor.displayName

    return when {
      // Include if this test matches the target mode
      displayName.contains("[$targetMode]") -> true

      // Exclude tests for other modes
      displayName.contains("[${IdeRunMode.MONOLITH}]") ||
      displayName.contains("[${IdeRunMode.SPLIT}]") -> false

      // Include non-mode-specific tests (run in both phases)
      else -> true
    }
  }
}
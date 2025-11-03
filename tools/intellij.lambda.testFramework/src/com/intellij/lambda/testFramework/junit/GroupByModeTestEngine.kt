package com.intellij.lambda.testFramework.junit

import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder

/**
 * Custom TestEngine that wraps JUnit Jupiter and enables grouped-by-mode test execution.
 *
 * When a test class is annotated with @GroupTestsByMode, this engine ensures
 * tests execute in two phases:
 * 1. All MONOLITH mode tests across all methods
 * 2. All SPLIT mode tests across all methods
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
    println("GroupByModeTestEngine: Starting discovery with request: ${discoveryRequest.getSelectorsByType(ClassSelector::class.java).map { it.className }}")

    // Filter to only get classes with @GroupTestsByMode
    val groupedClassSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)
      .filter { selector ->
        try {
          val clazz = Class.forName(selector.className)
          val hasAnnotation = clazz.isAnnotationPresent(GroupTestsByMode::class.java)
          println("GroupByModeTestEngine: Class ${selector.className} has @GroupTestsByMode: $hasAnnotation")
          hasAnnotation
        }
        catch (e: ClassNotFoundException) {
          false
        }
      }

    if (groupedClassSelectors.isEmpty()) {
      println("GroupByModeTestEngine: No tests with @GroupTestsByMode found, returning empty descriptor")
      return EngineDescriptor(uniqueId, "Group by Mode (no tests)")
    }

    println("GroupByModeTestEngine: Found ${groupedClassSelectors.size} classes with @GroupTestsByMode: ${groupedClassSelectors.map { it.className }}")

    // Create custom engine descriptor that stores the configuration
    val engineDescriptor = GroupedModeEngineDescriptor(
      uniqueId,
      groupedClassSelectors,
      discoveryRequest.configurationParameters
    )

    // For each class, create a synthetic test structure that reflects the grouped execution
    for (selector in groupedClassSelectors) {
      val testClass = try {
        Class.forName(selector.className)
      }
      catch (e: ClassNotFoundException) {
        println("GroupByModeTestEngine: Could not load class ${selector.className}")
        continue
      }

      val classDescriptor = GroupedClassDescriptor(
        uniqueId.append("class", selector.className),
        selector.className,
        ClassSource.from(testClass)
      )
      engineDescriptor.addChild(classDescriptor)

      // Add mode containers
      val monolithDescriptor = ModeContainerDescriptor(
        classDescriptor.uniqueId.append("mode", IdeRunMode.MONOLITH.name),
        IdeRunMode.MONOLITH,
        selector.className
      )
      classDescriptor.addChild(monolithDescriptor)

      val splitDescriptor = ModeContainerDescriptor(
        classDescriptor.uniqueId.append("mode", IdeRunMode.SPLIT.name),
        IdeRunMode.SPLIT,
        selector.className
      )
      classDescriptor.addChild(splitDescriptor)
    }

    return engineDescriptor
  }

  override fun execute(executionRequest: ExecutionRequest) {
    val rootDescriptor = executionRequest.rootTestDescriptor
    val listener = executionRequest.engineExecutionListener

    println("GroupByModeTestEngine: Executing with descriptor type: ${rootDescriptor.javaClass.simpleName}")

    if (rootDescriptor !is GroupedModeEngineDescriptor) {
      println("GroupByModeTestEngine: Unknown descriptor type, skipping")
      return
    }

    listener.executionStarted(rootDescriptor)

    try {
      val classSelectors = rootDescriptor.classSelectors

      if (classSelectors.isEmpty()) {
        println("GroupByModeTestEngine: No classes to execute")
        listener.executionFinished(rootDescriptor, TestExecutionResult.successful())
        return
      }

      // IMPORTANT: Execute by MODE first, not by class
      // This ensures all MONOLITH tests run, then all SPLIT tests
      val modes = listOf(IdeRunMode.MONOLITH, IdeRunMode.SPLIT)

      // Start all class descriptors once at the beginning
      for (classDescriptor in rootDescriptor.children) {
        listener.executionStarted(classDescriptor)
      }

      for (mode in modes) {
        // For each mode, execute ALL classes
        for (classDescriptor in rootDescriptor.children) {
          try {
            val modeDescriptor = classDescriptor.children.find {
              it is ModeContainerDescriptor && it.mode == mode
            } as? ModeContainerDescriptor

            if (modeDescriptor != null) {
              executeMode(modeDescriptor, classSelectors, executionRequest, listener, isLastMode = mode == modes.last())
            }
          }
          catch (e: Exception) {
            // Don't finish the class descriptor yet on error
            println("GroupByModeTestEngine: Error in $mode mode: ${e.message}")
            e.printStackTrace()
          }
        }
      }

      // Finish all class descriptors once at the end
      for (classDescriptor in rootDescriptor.children) {
        listener.executionFinished(classDescriptor, TestExecutionResult.successful())
      }

      listener.executionFinished(rootDescriptor, TestExecutionResult.successful())
    }
    catch (e: Exception) {
      listener.executionFinished(rootDescriptor, TestExecutionResult.failed(e))
    }
  }

  private fun executeMode(
    modeDescriptor: ModeContainerDescriptor,
    classSelectors: List<ClassSelector>,
    originalExecutionRequest: ExecutionRequest,
    listener: EngineExecutionListener,
    isLastMode: Boolean,
  ) {
    println("=== Executing tests in ${modeDescriptor.mode} mode for ${modeDescriptor.className} ===")

    listener.executionStarted(modeDescriptor)

    try {
      val configParams = originalExecutionRequest.configurationParameters

      // Find the selector for this class
      val classSelector = classSelectors.find { it.className == modeDescriptor.className }
      if (classSelector == null) {
        listener.executionFinished(modeDescriptor, TestExecutionResult.successful())
        return
      }

      // Convert configuration parameters to Map and ADD the current mode as a filter
      val configMap = mutableMapOf<String, String>()
      configParams.keySet().forEach { key ->
        configParams.get(key).ifPresent { value ->
          configMap[key] = value
        }
      }

      // Add mode filter configuration - this tells MonolithAndSplitModeContextProvider
      // to only generate tests for this specific mode
      configMap["test.mode.filter"] = modeDescriptor.mode.name

      // Create discovery request
      val freshRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(listOf(classSelector))
        .configurationParameters(configMap)
        .build()

      // Discover tests - now Jupiter will only discover tests for the target mode
      val jupiterUniqueId = UniqueId.forEngine(JUPITER_ENGINE_ID)
      val freshJupiterDescriptor = jupiterEngine.discover(freshRequest, jupiterUniqueId)

      println("GroupByModeTestEngine: Found ${freshJupiterDescriptor.descendants.size} descendants for ${modeDescriptor.mode}")

      // Create translating listener that reports under our mode descriptor
      val translatingListener = TranslatingExecutionListener(
        listener,
        modeDescriptor,
        modeDescriptor.mode
      )

      // Reuse the original request's store to share application state
      val executionRequest = ExecutionRequest.create(
        freshJupiterDescriptor,
        translatingListener,
        configParams,
        originalExecutionRequest.outputDirectoryProvider,
        originalExecutionRequest.store
      )

      jupiterEngine.execute(executionRequest)

      // Only allow cleanup on the last mode to keep application alive between modes
      if (!isLastMode) {
        println("GroupByModeTestEngine: Skipping cleanup between modes")
      }
      else {
        listener.executionFinished(modeDescriptor, TestExecutionResult.successful())
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
      listener.executionFinished(modeDescriptor, TestExecutionResult.failed(e))
    }
  }
}

/**
 * Custom engine descriptor that stores configuration for grouped execution
 */
class GroupedModeEngineDescriptor(
  uniqueId: UniqueId,
  val classSelectors: List<ClassSelector>,
  val configurationParameters: ConfigurationParameters,
) : EngineDescriptor(uniqueId, "Group by Mode")


/**
 * Descriptor for a class that will be executed in grouped mode
 */
class GroupedClassDescriptor(
  uniqueId: UniqueId,
  private val className: String,
  classSource: TestSource?,
) : org.junit.platform.engine.support.descriptor.AbstractTestDescriptor(
  uniqueId,
  className,
  classSource ?: org.junit.platform.engine.support.descriptor.ClassSource.from(className)
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
) : org.junit.platform.engine.support.descriptor.AbstractTestDescriptor(uniqueId, "[$mode]") {

  override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER

  override fun mayRegisterTests(): Boolean = true
}

/**
 * Listener that translates Jupiter execution events to our custom descriptors
 */
class TranslatingExecutionListener(
  private val delegate: EngineExecutionListener,
  private val modeContainer: ModeContainerDescriptor,
  private val targetMode: IdeRunMode,
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

    val synthetic = getOrCreateSyntheticDescriptor(testDescriptor)
    delegate.executionSkipped(synthetic, reason)
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    // Skip engine and class containers from Jupiter
    if (testDescriptor.type == TestDescriptor.Type.CONTAINER &&
        (testDescriptor.uniqueId.engineId.orElse(null) == "junit-jupiter" ||
         testDescriptor.source.orElse(null) is ClassSource)) {
      return
    }

    if (!shouldInclude(testDescriptor)) return

    val synthetic = getOrCreateSyntheticDescriptor(testDescriptor)
    startedDescriptors.add(synthetic.uniqueId)
    delegate.executionStarted(synthetic)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    // Skip engine and class containers from Jupiter
    if (testDescriptor.type == TestDescriptor.Type.CONTAINER &&
        (testDescriptor.uniqueId.engineId.orElse(null) == "junit-jupiter" ||
         testDescriptor.source.orElse(null) is ClassSource)) {
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
      displayName.contains("[$targetMode]") -> true
      displayName.contains("[${IdeRunMode.MONOLITH}]") ||
      displayName.contains("[${IdeRunMode.SPLIT}]") -> false
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

    val synthetic = object : org.junit.platform.engine.support.descriptor.AbstractTestDescriptor(
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
}
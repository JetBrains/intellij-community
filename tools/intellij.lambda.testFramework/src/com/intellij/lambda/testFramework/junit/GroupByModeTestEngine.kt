package com.intellij.lambda.testFramework.junit

import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import java.lang.reflect.Field
import java.util.*

internal const val JUPITER_ENGINE_ID = "junit-jupiter"

private val jupiterUniqueId = UniqueId.forEngine(JUPITER_ENGINE_ID)

/**
 * Custom TestEngine that wraps JUnit Jupiter and enables grouped by [IdeRunMode] test execution.
 *
 * When a test class is annotated with [ExecuteInMonolithAndSplitMode], this engine ensures
 * tests execute in two phases:
 * 1. All MONOLITH mode tests across all methods
 * 2. All SPLIT mode tests across all methods
 */
class GroupByModeTestEngine : TestEngine {
  companion object {
    const val ENGINE_ID = "group-by-mode"
    val engineClassName = GroupByModeTestEngine::class.simpleName

    private val annotationName = ExecuteInMonolithAndSplitMode::class.simpleName

    private fun log(message: String) = logOutput("[$engineClassName]: $message")
  }

  private val jupiterEngine: TestEngine by lazy {
    ServiceLoader.load(TestEngine::class.java).find { it.id == JUPITER_ENGINE_ID }
    ?: error("JUnit Jupiter engine not found")
  }

  override fun getId(): String = ENGINE_ID

  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
    if (!isGroupedExecutionEnabled) {
      log("$engineClassName will not be used because grouped execution is disabled")
      return GroupedModeEngineDescriptor(uniqueId, listOf(), discoveryRequest.configurationParameters, emptyList())
    }

    // Log ALL selector types to debug
    log("Starting discovery - all selectors:")
    log("  ClassSelectors: ${discoveryRequest.getSelectorsByType(ClassSelector::class.java).map { it.className }}")
    log("  PackageSelectors: ${discoveryRequest.getSelectorsByType(PackageSelector::class.java).map { it.packageName }}")
    log("  ClasspathRootSelectors: ${discoveryRequest.getSelectorsByType(ClasspathRootSelector::class.java).map { it.classpathRoot }}")

    val postDiscoveryFilters = try {
      val requestField = findFieldInHierarchy(discoveryRequest, "request")
      val launcherRequest = requestField?.get(discoveryRequest)
      log("LauncherRequest type: ${launcherRequest?.javaClass?.name}")
      val postDiscoveryFiltersField = launcherRequest?.let { findFieldInHierarchy(it, "postDiscoveryFilters") }
      log("PostDiscoveryFiltersField found: ${postDiscoveryFiltersField != null}")
      @Suppress("UNCHECKED_CAST")
      val filters = (postDiscoveryFiltersField?.get(launcherRequest) as? List<PostDiscoveryFilter>) ?: emptyList()
      log("Extracted ${filters.size} post-discovery filters: ${filters.map { it.javaClass.simpleName }}")
      filters
    }
    catch (e: Exception) {
      log("Could not reflectively access postDiscoveryFilters: ${e.message}")
      e.printStackTrace()
      emptyList<PostDiscoveryFilter>()
    }

    // IMPORTANT: Delegate to Jupiter for full discovery
    // This handles ClasspathRootSelector, PackageSelector, ClassSelector, etc.
    log("Delegating to Jupiter for full discovery...")
    val jupiterDescriptor = jupiterEngine.discover(discoveryRequest, jupiterUniqueId)
    log("Jupiter found ${jupiterDescriptor.descendants.size} total test descriptors")

    // Extract all unique class names from Jupiter's discovery
    val discoveredClasses = jupiterDescriptor.descendants
      .mapNotNull { descriptor ->
        (descriptor.source.orElse(null) as? ClassSource)?.className
      }
      .distinct()

    log("Extracted ${discoveredClasses.size} unique classes from Jupiter discovery: $discoveredClasses")

    // Filter to only get classes with @ExecuteInMonolithAndSplitMode
    val groupedClassSelectors = discoveredClasses
      .filter { className ->
        try {
          val clazz = Class.forName(className)
          val hasAnnotation = clazz.isAnnotationPresent(ExecuteInMonolithAndSplitMode::class.java)
          log("Class $className has @$annotationName: $hasAnnotation")
          hasAnnotation
        }
        catch (e: Exception) {
          log("Could not check class $className: ${e.message}")
          false
        }
      }
      .map { className ->
        org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(className)
      }

    if (groupedClassSelectors.isEmpty()) {
      log("No tests with @$annotationName found, returning empty descriptor")
      return EngineDescriptor(uniqueId, "Group by Mode (no tests)")
    }

    log("Found ${groupedClassSelectors.size} classes with @$annotationName: ${groupedClassSelectors.map { it.className }}")

    // Create custom engine descriptor that stores the configuration
    val engineDescriptor = GroupedModeEngineDescriptor(
      uniqueId,
      groupedClassSelectors,
      discoveryRequest.configurationParameters,
      postDiscoveryFilters
    )

    // For each class, create a synthetic test structure that reflects the grouped execution
    for (selector in groupedClassSelectors) {
      val testClass = try {
        Class.forName(selector.className)
      }
      catch (e: ClassNotFoundException) {
        log("Could not load class ${selector.className}")
        continue
      }

      val classDescriptor = GroupedClassDescriptor(
        uniqueId.append("class", selector.className),
        selector.className,
        ClassSource.from(testClass)
      )
      engineDescriptor.addChild(classDescriptor)

      // Add mode containers
      IdeRunMode.entries.forEach { mode ->
        classDescriptor.addChild(
          ModeContainerDescriptor(classDescriptor.uniqueId.append("mode", mode.name), mode, selector.className)
        )
      }
    }

    return engineDescriptor
  }

  override fun execute(executionRequest: ExecutionRequest) {
    val rootDescriptor = executionRequest.rootTestDescriptor
    val listener = executionRequest.engineExecutionListener

    log("Executing with descriptor type: ${rootDescriptor.javaClass.simpleName}")

    if (rootDescriptor !is GroupedModeEngineDescriptor) {
      log("Unknown descriptor type, skipping")
      return
    }

    listener.executionStarted(rootDescriptor)

    try {
      val classSelectors = rootDescriptor.classSelectors

      if (classSelectors.isEmpty()) {
        log("No classes to execute")
        listener.executionFinished(rootDescriptor, TestExecutionResult.successful())
        return
      }

      // IMPORTANT: Execute by MODE first, not by class
      // This ensures all MONOLITH tests run, then all SPLIT tests
      val modes = IdeRunMode.entries.sorted()

      // Start all class descriptors once at the beginning
      for (classDescriptor in rootDescriptor.children) {
        listener.executionStarted(classDescriptor)
      }

      for (mode in modes) {
        val results = mutableListOf<ModeDescriptorResult>()

        // For each mode, execute ALL classes
        for (classDescriptor in rootDescriptor.children) {
          try {
            val modeDescriptor = classDescriptor.children.find {
              it is ModeContainerDescriptor && it.mode == mode
            } as? ModeContainerDescriptor

            if (modeDescriptor != null) {
              results.add(executeMode(modeDescriptor, classSelectors, executionRequest, listener))
            }
          }
          catch (e: Exception) {
            // Don't finish the class descriptor yet on error
            log("Error in $mode mode: ${e.message}")
            e.printStackTrace()
          }
        }

        // Only allow cleanup after the last mode to keep application alive between modes
        results.forEach { listener.executionFinished(it.descriptor, it.result) }
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
  ): ModeDescriptorResult {
    println("=== Executing tests in ${modeDescriptor.mode} mode for ${modeDescriptor.className} ===")

    listener.executionStarted(modeDescriptor)

    try {
      val rootDescriptor = originalExecutionRequest.rootTestDescriptor as GroupedModeEngineDescriptor
      val postDiscoveryFilters = rootDescriptor.postDiscoveryFilters
      val configParams = originalExecutionRequest.configurationParameters

      val classSelector = classSelectors.find { it.className == modeDescriptor.className }
      if (classSelector == null) {
        return ModeDescriptorResult(modeDescriptor, TestExecutionResult.successful())
      }

      val configMap = mutableMapOf<String, String>()
      configParams.keySet().forEach { key ->
        configParams.get(key).ifPresent { value ->
          configMap[key] = value
        }
      }

      // Add mode filter configuration - this tells MonolithAndSplitModeContextProvider
      // to only generate tests for this specific mode
      configMap["ide.run.mode.filter"] = modeDescriptor.mode.name

      // Create discovery request
      log("Creating fresh discovery request with ${postDiscoveryFilters.size} filters")
      val freshRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(listOf(classSelector))
        .configurationParameters(configMap)
        .build()

      val freshJupiterDescriptor = jupiterEngine.discover(freshRequest, jupiterUniqueId)

      log("Found ${freshJupiterDescriptor.descendants.size} descendants for ${modeDescriptor.mode}")

      // Create translating listener that reports under our mode descriptor
      val translatingListener = TranslatingExecutionListener(
        listener,
        modeDescriptor
      )

      // Wrap with filtering listener that applies post-discovery filters
      val filteringListener = FilteringExecutionListener(
        translatingListener,
        postDiscoveryFilters
      )

      // Reuse the original request's store to share application state
      val executionRequest = ExecutionRequest.create(
        freshJupiterDescriptor,
        filteringListener,
        configParams,
        originalExecutionRequest.outputDirectoryProvider,
        originalExecutionRequest.store
      )

      jupiterEngine.execute(executionRequest)
      return ModeDescriptorResult(modeDescriptor, TestExecutionResult.successful())
    }
    catch (e: Exception) {
      return ModeDescriptorResult(modeDescriptor, TestExecutionResult.failed(e))
    }
  }
}


/**
 * Finds a field in an object's class hierarchy, including superclasses.
 */
private fun findFieldInHierarchy(obj: Any, fieldName: String): Field? {
  var currentClass: Class<*>? = obj.javaClass
  while (currentClass != null) {
    try {
      val field = currentClass.getDeclaredField(fieldName)
      field.isAccessible = true
      return field
    }
    catch (e: NoSuchFieldException) {
      // Field not in this class, check its superclass
      currentClass = currentClass.superclass
    }
  }

  return null
}


data class ModeDescriptorResult(val descriptor: ModeContainerDescriptor, val result: TestExecutionResult)
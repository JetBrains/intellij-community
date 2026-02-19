package com.intellij.lambda.testFramework.junit

import com.intellij.openapi.diagnostic.fileLogger
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.Filter
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import java.lang.reflect.Field
import java.util.ServiceLoader

internal const val JUPITER_ENGINE_ID = "junit-jupiter"

private val jupiterUniqueId = UniqueId.forEngine(JUPITER_ENGINE_ID)

private val LOG = fileLogger()

/**
 * Custom TestEngine that wraps JUnit Jupiter and enables grouped by [IdeRunMode] test execution.
 *
 * When a test class is annotated with [RunInMonolithAndSplitMode], this engine ensures
 * tests execute in two phases:
 * 1. All MONOLITH mode tests across all methods
 * 2. All SPLIT mode tests across all methods
 */
class GroupByModeTestEngine : TestEngine {
  companion object {
    const val ENGINE_ID = "group-by-mode"
    val engineClassName = GroupByModeTestEngine::class.simpleName

    private val annotationName = RunInMonolithAndSplitMode::class.simpleName
  }

  private val jupiterEngine: TestEngine by lazy {
    ServiceLoader.load(TestEngine::class.java).find { it.id == JUPITER_ENGINE_ID }
    ?: error("JUnit Jupiter engine not found")
  }

  override fun getId(): String = ENGINE_ID

  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
    if (!isGroupedExecutionEnabled) {
      LOG.info("$engineClassName will not be used because grouped execution is disabled")
      return GroupedModeEngineDescriptor(uniqueId, listOf(), discoveryRequest.configurationParameters, emptyList())
    }

    // Log ALL selector types to debug
    LOG.info("Starting discovery - all selectors:")
    LOG.info("  ClassSelectors: ${discoveryRequest.getSelectorsByType(ClassSelector::class.java).map { it.className }}")
    LOG.info("  PackageSelectors: ${discoveryRequest.getSelectorsByType(PackageSelector::class.java).map { it.packageName }}")
    LOG.info("  ClasspathRootSelectors: ${discoveryRequest.getSelectorsByType(ClasspathRootSelector::class.java).map { it.classpathRoot }}")

    val postDiscoveryFilters = try {
      val requestField = findFieldInHierarchy(discoveryRequest, "request")
      val launcherRequest = requestField?.get(discoveryRequest)
      LOG.info("LauncherRequest type: ${launcherRequest?.javaClass?.name}")
      val postDiscoveryFiltersField = launcherRequest?.let { findFieldInHierarchy(it, "postDiscoveryFilters") }
      LOG.info("PostDiscoveryFiltersField found: ${postDiscoveryFiltersField != null}")
      @Suppress("UNCHECKED_CAST")
      val filters = (postDiscoveryFiltersField?.get(launcherRequest) as? List<PostDiscoveryFilter>) ?: emptyList()
      LOG.info("Extracted ${filters.size} post-discovery filters: ${filters.map { it.javaClass.simpleName }}")
      filters
    }
    catch (e: Exception) {
      LOG.error("Could not reflectively access postDiscoveryFilters: ${e.message}")
      e.printStackTrace()
      emptyList<PostDiscoveryFilter>()
    }

    // IMPORTANT: Delegate to Jupiter for full discovery
    // This handles ClasspathRootSelector, PackageSelector, ClassSelector, etc.
    LOG.info("Delegating to Jupiter for full discovery...")
    val jupiterDescriptor = jupiterEngine.discover(discoveryRequest, jupiterUniqueId)
    LOG.info("Jupiter found ${jupiterDescriptor.descendants.size} total test descriptors")

    // Extract all unique class names from Jupiter's discovery
    val discoveredClasses = jupiterDescriptor.descendants
      .mapNotNull { descriptor ->
        (descriptor.source.orElse(null) as? ClassSource)?.className
      }
      .distinct()

    LOG.info("Extracted ${discoveredClasses.size} unique classes from Jupiter discovery: $discoveredClasses")

    // Filter to only get classes with @ExecuteInMonolithAndSplitMode
    val groupedClassSelectors = discoveredClasses
      .filter { className ->
        try {
          val clazz = Class.forName(className)
          val hasAnnotation = clazz.isAnnotationPresent(RunInMonolithAndSplitMode::class.java)
          LOG.info("Class $className has @$annotationName: $hasAnnotation")
          hasAnnotation
        }
        catch (e: Exception) {
          LOG.info("Could not check class $className: ${e.message}")
          false
        }
      }
      .map { className ->
        org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(className)
      }

    if (groupedClassSelectors.isEmpty()) {
      LOG.info("No tests with @$annotationName found, returning empty descriptor")
      return EngineDescriptor(uniqueId, "Group by Mode (no tests)")
    }

    LOG.info("Found ${groupedClassSelectors.size} classes with @$annotationName: ${groupedClassSelectors.map { it.className }}")

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
        LOG.info("Could not load class ${selector.className}")
        continue
      }

      val classDescriptor = GroupedClassDescriptor(
        uniqueId.append("class", selector.className),
        selector.className,
        ClassSource.from(testClass)
      )
      engineDescriptor.addChild(classDescriptor)

      getModesToRun(testClass).forEach { mode ->
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

    LOG.info("Executing with descriptor type: ${rootDescriptor.javaClass.simpleName}")

    if (rootDescriptor !is GroupedModeEngineDescriptor) {
      LOG.info("Unknown descriptor type, skipping")
      return
    }

    listener.executionStarted(rootDescriptor)

    try {
      val classSelectors = rootDescriptor.classSelectors

      if (classSelectors.isEmpty()) {
        LOG.info("No classes to execute")
        listener.executionFinished(rootDescriptor, TestExecutionResult.successful())
        return
      }

      // IMPORTANT: Group execution by MODE first, not by class
      for (mode in IdeRunMode.entries.sorted()) {
        for (classDescriptor in rootDescriptor.children) {
          listener.executionStarted(classDescriptor)

          try {
            val modeDescriptor = classDescriptor.children.find {
              it is ModeContainerDescriptor && it.mode == mode
            } as? ModeContainerDescriptor

            if (modeDescriptor != null) {
              executeMode(modeDescriptor, classSelectors, executionRequest, listener)
            }
          }
          catch (e: Throwable) {
            LOG.info("Error in $mode mode: ${e.message}")
            e.printStackTrace()
            listener.executionFinished(classDescriptor, TestExecutionResult.failed(e))
          }

          listener.executionFinished(classDescriptor, TestExecutionResult.successful())
        }
      }

      listener.executionFinished(rootDescriptor, TestExecutionResult.successful())
    }
    catch (e: Throwable) {
      listener.executionFinished(rootDescriptor, TestExecutionResult.failed(e))
    }
  }

  private fun executeMode(
    modeDescriptor: ModeContainerDescriptor,
    classSelectors: List<ClassSelector>,
    originalExecutionRequest: ExecutionRequest,
    listener: EngineExecutionListener,
  ): Unit {
    LOG.info("=== Executing tests in ${modeDescriptor.mode} mode for ${modeDescriptor.className} ===")

    listener.executionStarted(modeDescriptor)

    try {
      val rootDescriptor = originalExecutionRequest.rootTestDescriptor as GroupedModeEngineDescriptor
      val postDiscoveryFilters = rootDescriptor.postDiscoveryFilters
      val configParams = originalExecutionRequest.configurationParameters

      val classSelector = classSelectors.find { it.className == modeDescriptor.className }
      if (classSelector == null) {
        return listener.executionFinished(modeDescriptor, TestExecutionResult.successful())
      }

      val configMap = mutableMapOf<String, String>()
      configParams.keySet().forEach { key ->
        configParams.get(key).ifPresent { value ->
          configMap[key] = value
        }
      }

      configMap["ide.run.mode.filter"] = modeDescriptor.mode.name

      LOG.info("Creating fresh discovery request with ${postDiscoveryFilters.size} filters")
      val freshRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(listOf(classSelector))
        .configurationParameters(configMap)
        .build()

      val freshJupiterDescriptor = jupiterEngine.discover(freshRequest, jupiterUniqueId)
      LOG.info("Found ${freshJupiterDescriptor.descendants.size} descendants for ${modeDescriptor.mode}")

      applyPostDiscoveryFilters(freshJupiterDescriptor, postDiscoveryFilters)
      LOG.info("After filtering ${freshJupiterDescriptor.descendants.size} descendants remain: ${freshJupiterDescriptor.descendants.joinToString(separator = ", ") { it.displayName }}")

      val translatingListener = TranslatingExecutionListener(
        listener,
        modeDescriptor
      )

      val executionRequest = ExecutionRequest.create(
        freshJupiterDescriptor,
        translatingListener,
        configParams,
        originalExecutionRequest.outputDirectoryProvider,
        originalExecutionRequest.store
      )

      jupiterEngine.execute(executionRequest)
      return listener.executionFinished(modeDescriptor, TestExecutionResult.successful())
    }
    catch (e: Exception) {
      return listener.executionFinished(modeDescriptor, TestExecutionResult.failed(e))
    }
  }

  /**
   * Apply post-discovery filters to prune test descriptors from the tree.
   * This removes filtered tests so they won't be executed at all.
   */
  private fun applyPostDiscoveryFilters(
    descriptor: TestDescriptor,
    postDiscoveryFilters: List<PostDiscoveryFilter>,
  ) {
    if (postDiscoveryFilters.isEmpty() || descriptor.children.isEmpty()) return

    // Apply post-discovery filters manually, since IntelliJ test starter wraps method selectors as post-discovery filters
    val composedFilter = Filter.composeFilters(postDiscoveryFilters)

    val childrenToRemove = mutableListOf<TestDescriptor>()

    for (child in descriptor.children) {
      val filterResult = composedFilter.apply(child)

      if (filterResult.excluded()) {
        LOG.info("Filtering out test: ${child.displayName}")
        childrenToRemove.add(child)
      }
      else {
        LOG.info("Keeping test/container: ${child.displayName}")
      }
    }

    childrenToRemove.forEach { child ->
      descriptor.removeChild(child)
    }

    descriptor.children.forEach {
      applyPostDiscoveryFilters(it, postDiscoveryFilters)
    }

    return
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
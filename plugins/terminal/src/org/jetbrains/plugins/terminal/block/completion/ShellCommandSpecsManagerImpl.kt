// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellMergedCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonBasedCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonCommandSpecsUtil.loadAndParseJson
import org.jetbrains.terminal.completion.ShellCommand
import java.time.Duration

/**
 * Manages the [ShellCommandSpec]'s provided by [ShellCommandSpecsProvider]'s.
 *
 * Command specs can be of two types.
 *
 * ##### Kotlin-code-based specs ([ShellCommandSpecImpl][org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl])
 *
 * They are lazy by default, and there are no problems with them.
 *
 * ##### Json based specs ([ShellJsonBasedCommandSpec])
 *
 * They are provided by [ShellJsonCommandSpecsProvider]'s.
 *
 * There is an explicit laziness mechanism.
 * If the spec contains a not null [fullSpecRef][ShellJsonBasedCommandSpec.fullSpecRef] property,
 * then it means that this spec contains only basic data like names and description.
 * And there are no subcommands, options and arguments.
 * Let's call such specs **Light** specs.
 * To get the full version of the spec, [getFullCommandSpec] method should be used.
 *
 * ##### Merged specs ([ShellMergedCommandSpec])
 *
 * Merged specs consist of several json-based and code-based specs.
 * So, to get the full version of this spec, [getFullCommandSpec] method should be used.
 */
@Service
internal class ShellCommandSpecsManagerImpl : ShellCommandSpecsManager {

  val tracer = TelemetryManager.getTracer(TerminalCompletionScope)

  /**
   * Cache for all **Light** json-based and code based specs with resolved conflicts.
   * Key is the name of the command.
   */
  private val lightSpecsCache: Cache<String, ShellCommandSpec> = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  /** Cache for json-based **Light** command spec providers. Key is the name of the command */
  private val jsonBasedSpecProviders: Cache<String, ShellJsonCommandSpecsProvider> = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  /** Cache for loaded [ShellJsonBasedCommandSpec]'s. Key is the name of the command. */
  private val fullSpecsCache: Cache<String, ShellJsonBasedCommandSpec> = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  init {
    ShellCommandSpecsProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ShellCommandSpecsProvider> {
      override fun extensionAdded(extension: ShellCommandSpecsProvider, pluginDescriptor: PluginDescriptor) {
        clearCaches()
      }

      override fun extensionRemoved(extension: ShellCommandSpecsProvider, pluginDescriptor: PluginDescriptor) {
        clearCaches()
      }
    })
  }

  /**
   * @return the full spec for provided [commandName] if any.
   */
  override suspend fun getCommandSpec(commandName: String): ShellCommandSpec? {
    val spec = getLightCommandSpec(commandName) ?: return null
    return getFullCommandSpec(spec)
  }

  /**
   * If the spec is json-based, then it can be a **Light** spec sometimes.
   * This method is loading and returning a full spec for such specs.
   * If the spec is code-based, then the same spec is returned, because the lazy loading is implemented inside it.
   * If the spec is merged, the full specs are loaded for all its consisting specs.
   */
  override suspend fun getFullCommandSpec(spec: ShellCommandSpec): ShellCommandSpec {
    if (spec is ShellMergedCommandSpec) {
      val fullBaseSpec = spec.baseSpec?.let { getFullCommandSpec(it) }
      val fullOverrideSpecs = spec.overridingSpecs.map { getFullCommandSpec(it) }
      return ShellMergedCommandSpec(fullBaseSpec, fullOverrideSpecs, spec.parentNames)
    }
    if (spec !is ShellJsonBasedCommandSpec || spec.fullSpecRef == null) {
      return spec
    }
    val specRef = spec.fullSpecRef!!
    fullSpecsCache.getIfPresent(specRef)?.let { return it }
    val fullSpec = loadFullCommandSpec(specRef) ?: run {
      LOG.error("Failed to load full command spec '$specRef'")
      return spec
    }
    fullSpecsCache.put(specRef, fullSpec)
    return fullSpec
  }

  /**
   * If the [commandName] is described by json-based spec, it will return a **Light** version of the command specification:
   * only names, description, and the [ShellJsonBasedCommandSpec.fullSpecRef] reference for loading full specification.
   * Otherwise, it will return the same spec as [getCommandSpec] returns, because code-based spec implementation is already lazy.
   * Intended that this method should return fast in most of the cases, because it should not load the whole command specification.
   */
  fun getLightCommandSpec(commandName: String): ShellCommandSpec? {
    loadCommandSpecsIfNeeded()
    return lightSpecsCache.getIfPresent(commandName)
  }

  private fun getJsonCommandSpecProvider(commandName: String): ShellJsonCommandSpecsProvider? {
    loadCommandSpecsIfNeeded()
    return jsonBasedSpecProviders.getIfPresent(commandName)
  }

  private fun loadCommandSpecsIfNeeded() {
    if (lightSpecsCache.estimatedSize() != 0L && jsonBasedSpecProviders.estimatedSize() != 0L) {
      return
    }

    val specsDataMap: MultiMap<String, ShellCommandSpecData> = loadCommandSpecs()

    for ((name, specs) in specsDataMap.entrySet()) {
      val specData = if (specs.size > 1) {
        resolveSpecsConflict(specs)
      }
      else {
        specs.first()
      }

      lightSpecsCache.put(name, specData.spec)

      val jsonSpecData = specs.find { it.spec is ShellJsonBasedCommandSpec }
      if (jsonSpecData != null) {
        jsonBasedSpecProviders.put(name, jsonSpecData.provider as ShellJsonCommandSpecsProvider)
      }
    }
  }

  private fun loadCommandSpecs(): MultiMap<String, ShellCommandSpecData> {
    tracer.spanBuilder("terminal-load-command-specs").use {
      val specsDataMap = MultiMap<String, ShellCommandSpecData>()
      for (provider in ShellCommandSpecsProvider.EP_NAME.extensionList) {
        tracer.spanBuilder(provider.javaClass.name).use {
          val specInfos = provider.getCommandSpecs()
          for (specInfo in specInfos) {
            val specData = ShellCommandSpecData(specInfo.spec, specInfo.conflictStrategy, provider)
            specsDataMap.putValue(specData.spec.name, specData)
          }
        }
      }
      return specsDataMap
    }
  }

  private fun clearCaches() {
    lightSpecsCache.invalidateAll()
    jsonBasedSpecProviders.invalidateAll()
    fullSpecsCache.invalidateAll()
  }

  private fun resolveSpecsConflict(specs: Collection<ShellCommandSpecData>): ShellCommandSpecData {
    assert(specs.size > 1)
    val replaceSpecs = specs.filter { it.conflictStrategy == ShellCommandSpecConflictStrategy.REPLACE }
    if (replaceSpecs.isNotEmpty()) {
      if (replaceSpecs.size > 1) {
        LOG.warn(conflictMessage(ShellCommandSpecConflictStrategy.REPLACE, specs))
      }
      return replaceSpecs.first()
    }

    val baseSpecs = specs.filter { it.conflictStrategy == ShellCommandSpecConflictStrategy.DEFAULT }
    if (baseSpecs.size > 1) {
      LOG.warn(conflictMessage(ShellCommandSpecConflictStrategy.DEFAULT, baseSpecs))
    }
    val baseSpecData = baseSpecs.firstOrNull()

    val overrideSpecs = specs.filter { it.conflictStrategy == ShellCommandSpecConflictStrategy.OVERRIDE }
    return if (overrideSpecs.size == 1 && baseSpecData == null) {
      overrideSpecs.single()  // Single overriding spec overrides nothing, so return it.
    }
    else if (overrideSpecs.isNotEmpty()) {
      val mergedSpec = ShellMergedCommandSpec(baseSpecData?.spec, overrideSpecs.map { it.spec })
      val provider = baseSpecData?.provider ?: overrideSpecs.first().provider
      ShellCommandSpecData(mergedSpec, ShellCommandSpecConflictStrategy.OVERRIDE, provider)
    }
    else baseSpecData!!  // If there are no overriding specs, then base spec should be present.
  }

  private fun conflictMessage(strategy: ShellCommandSpecConflictStrategy, specs: Collection<ShellCommandSpecData>): String {
    return "There are more than one shell command spec with ${strategy.name} conflict strategy for the same command: $specs\n" +
           "Taking only the first one."
  }

  /**
   * Loads the command spec referenced by [specRef].
   * [specRef] can be the main command name or the path of subcommand.
   * In the latter case, the [specRef] should be represented by the main command name and subcommand name divided by '/'.
   * For example, 'main/sub'.
   * The subcommand should be located in the directory named as the main command.
   * So the expected file structure should look like this:
   * - main.json
   * - main
   *     - sub.json
   *     - sub2.json
   */
  private suspend fun loadFullCommandSpec(specRef: String): ShellJsonBasedCommandSpec? {
    val mainCommand = if (specRef.contains('/')) {
      specRef.substringBefore('/')
    }
    else null
    // If it is the reference of the subcommand inside the main command, we need to get the main command spec.
    // Command spec should be loaded by the classloader of the main command provider.
    // If there is no main command, then we consider specRef as the main command.
    val specProvider = getJsonCommandSpecProvider(mainCommand ?: specRef) ?: return null
    val path = getSpecPath(specProvider, specRef)

    val command: ShellCommand = withContext(Dispatchers.IO) {
      loadAndParseJson(path, specProvider.javaClass.classLoader)
    } ?: return null
    return ShellJsonBasedCommandSpec(command.names.first(), command, parentNames = mainCommand?.let { listOf(it) } ?: emptyList())
  }

  /**
   * Returns the path of the command spec referenced by [specRef] inside the Jar.
   */
  private fun getSpecPath(provider: ShellJsonCommandSpecsProvider, specRef: String): String {
    val basePath = provider.commandSpecsPath.let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
    return "$basePath$specRef.json"
  }

  private data class ShellCommandSpecData(
    val spec: ShellCommandSpec,
    val conflictStrategy: ShellCommandSpecConflictStrategy,
    val provider: ShellCommandSpecsProvider
  )

  companion object {
    @JvmStatic
    fun getInstance(): ShellCommandSpecsManagerImpl = service()

    private val LOG: Logger = logger<ShellCommandSpecsManagerImpl>()
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.terminal.block.completion.ShellCommandSpecsManager
import com.intellij.terminal.block.completion.spec.ShellCommandSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
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
 */
@Service
internal class IJShellCommandSpecsManager : ShellCommandSpecsManager {
  /** Cache for all command specs with their providers. Key is the name of the command. */
  private val specsInfoCache: Cache<String, ShellCommandSpecInfo> = Caffeine.newBuilder()
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
        specsInfoCache.invalidateAll()
        fullSpecsCache.invalidateAll()
      }

      override fun extensionRemoved(extension: ShellCommandSpecsProvider, pluginDescriptor: PluginDescriptor) {
        specsInfoCache.invalidateAll()
        fullSpecsCache.invalidateAll()
      }
    })
  }

  /**
   * @return the full spec for provided [commandName] if any.
   */
  override suspend fun getCommandSpec(commandName: String): ShellCommandSpec? {
    val specInfo = getShellCommandSpecInfo(commandName) ?: return null
    return getFullCommandSpec(specInfo.spec)
  }

  /**
   * If the spec is json-based, then it can be a **Light** spec sometimes.
   * This method is loading and returning a full spec for such specs.
   * If the spec is code-based, then the same spec is returned, because the lazy loading is implemented inside it.
   */
  override suspend fun getFullCommandSpec(spec: ShellCommandSpec): ShellCommandSpec {
    if (spec !is ShellJsonBasedCommandSpec || spec.fullSpecRef == null) {
      return spec
    }
    val specRef = spec.fullSpecRef!!
    fullSpecsCache.getIfPresent(specRef)?.let { return it }
    val fullSpec = loadFullCommandSpec(specRef) ?: run {
      LOG.error("Failed to load full command spec '$specRef'")
      return spec
    }
    // We don't need to cache the code-based specs, because they are lazy by default
    if (fullSpec is ShellJsonBasedCommandSpec) {
      fullSpecsCache.put(specRef, fullSpec)
    }
    return fullSpec
  }

  /**
   * If the [commandName] is described by json-based spec, it will return a **Light** version of the command specification:
   * only names, description, and the [ShellJsonBasedCommandSpec.fullSpecRef] reference for loading full specification.
   * Otherwise, it will return the same spec as [getCommandSpec] returns, because code-based spec implementation is already lazy.
   * Intended that this method should return fast in most of the cases, because it should not load the whole command specification.
   */
  fun getLightCommandSpec(commandName: String): ShellCommandSpec? {
    return getShellCommandSpecInfo(commandName)?.spec
  }

  private fun getShellCommandSpecInfo(commandName: String): ShellCommandSpecInfo? {
    if (specsInfoCache.estimatedSize() == 0L) {
      val specsInfoMap = loadCommandSpecs()
      specsInfoCache.putAll(specsInfoMap)
    }
    return specsInfoCache.getIfPresent(commandName)
  }

  private fun loadCommandSpecs(): Map<String, ShellCommandSpecInfo> {
    val specsInfoMap = mutableMapOf<String, ShellCommandSpecInfo>()
    for (provider in ShellCommandSpecsProvider.EP_NAME.extensionList) {
      val specs = provider.getCommandSpecs()
      for (spec in specs) {
        for (name in spec.names) {
          // TODO: now only the last spec with the given name is effective, others are just skipped.
          //  We need a strategy of explicit merging / replacing the specs.
          specsInfoMap[name] = ShellCommandSpecInfo(spec, provider)
        }
      }
    }
    return specsInfoMap
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
  private suspend fun loadFullCommandSpec(specRef: String): ShellCommandSpec? {
    val (provider, path) = if (specRef.contains('/')) {
      // If it is the reference of the subcommand inside the main command, we first should get the main command spec
      // It is required to get the provider of the main command.
      // This is because the subcommand spec should be loaded using its classloader.
      val mainCommand = specRef.substringBefore('/')
      val mainCommandInfo = getShellCommandSpecInfo(mainCommand) ?: return null
      mainCommandInfo.provider to getSpecPath(mainCommandInfo.provider, specRef)
    }
    else {
      // It is just a reference of the other command, we should get it and load if it is a json based command spec.
      val commandInfo = getShellCommandSpecInfo(specRef) ?: return null
      if (commandInfo.spec !is ShellJsonBasedCommandSpec || commandInfo.spec.fullSpecRef == null) {
        return commandInfo.spec
      }
      commandInfo.provider to getSpecPath(commandInfo.provider, commandInfo.spec.fullSpecRef!!)
    }
    if (path == null) {
      return null
    }

    val command: ShellCommand = withContext(Dispatchers.IO) {
      loadAndParseJson(path, provider.javaClass.classLoader)
    } ?: return null
    return ShellJsonBasedCommandSpec(command)
  }

  /**
   * Returns the path of the command spec referenced by [specRef] inside the Jar.
   * It is implied that [provider] is a [ShellJsonCommandSpecsProvider],
   * because spec references are supported only in json based command specs.
   */
  private fun getSpecPath(provider: ShellCommandSpecsProvider, specRef: String): String? {
    val jsonSpecProvider = provider as? ShellJsonCommandSpecsProvider ?: run {
      LOG.error("Failed to get spec path for '$specRef'. Provider must be a ShellJsonCommandSpecsProvider")
      return null
    }
    val basePath = jsonSpecProvider.commandSpecsPath.let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
    return "$basePath$specRef.json"
  }

  private data class ShellCommandSpecInfo(val spec: ShellCommandSpec, val provider: ShellCommandSpecsProvider)

  companion object {
    @JvmStatic
    fun getInstance(): IJShellCommandSpecsManager = service()

    private val LOG: Logger = logger<IJShellCommandSpecsManager>()
  }
}
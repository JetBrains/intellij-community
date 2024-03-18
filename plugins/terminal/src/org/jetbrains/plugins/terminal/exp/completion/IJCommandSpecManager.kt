// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.terminal.completion.CommandSpecManager
import kotlinx.serialization.json.Json
import org.jetbrains.terminal.completion.ShellCommand
import java.io.IOException
import java.time.Duration

class IJCommandSpecManager : CommandSpecManager {
  private val commandsInfoCache: Cache<String, ShellCommandInfo> = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  private val commandSpecsCache: Cache<String, ShellCommand> = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  private val json: Json = Json {
    ignoreUnknownKeys = true
  }

  init {
    CommandSpecsBean.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<CommandSpecsBean> {
      override fun extensionAdded(extension: CommandSpecsBean, pluginDescriptor: PluginDescriptor) {
        commandsInfoCache.invalidateAll()
      }

      override fun extensionRemoved(extension: CommandSpecsBean, pluginDescriptor: PluginDescriptor) {
        commandsInfoCache.invalidateAll()
      }
    })
  }

  override fun getShortCommandSpec(commandName: String): ShellCommand? {
    return getShellCommandInfo(commandName)?.command
  }

  private fun getShellCommandInfo(commandName: String): ShellCommandInfo? {
    if (commandsInfoCache.estimatedSize() == 0L) {
      val commandsInfoMap = mutableMapOf<String, ShellCommandInfo>()
      for (bean in CommandSpecsBean.EP_NAME.extensionList) {
        loadCommandSpecs(bean, commandsInfoMap)
      }
      commandsInfoCache.putAll(commandsInfoMap)
    }
    return commandsInfoCache.getIfPresent(commandName)
  }

  private fun loadCommandSpecs(bean: CommandSpecsBean, destination: MutableMap<String, ShellCommandInfo>) {
    val commands: List<ShellCommand> = loadAndParseJson(bean.path, bean.pluginDesc.classLoader) ?: return
    for (command in commands) {
      for (name in command.names) {
        destination[name] = ShellCommandInfo(command, bean)
      }
    }
  }

  /**
   * [commandName] can be the main command name or the path of subcommand.
   * In the latter case, the [commandName] should be represented by the main command name and subcommand name divided by '/'.
   * For example, 'main/sub'.
   * The subcommand should be located in the directory named as the main command.
   * So the expected file structure should look like this:
   * - main.json
   * - main
   *     - sub.json
   *     - sub2.json
   */
  override suspend fun getCommandSpec(commandName: String): ShellCommand? {
    commandSpecsCache.getIfPresent(commandName)?.let { return it }

    val (commandInfo, path) = if (commandName.contains('/')) {
      val mainCommand = commandName.substringBefore('/')
      val mainCommandInfo = getShellCommandInfo(mainCommand) ?: return null
      val path = "${mainCommandInfo.bean.basePath}$commandName.json"
      mainCommandInfo to path
    }
    else {
      val commandInfo = getShellCommandInfo(commandName) ?: return null
      commandInfo to "${commandInfo.bean.basePath}${commandInfo.command.loadSpec}.json"
    }

    val command: ShellCommand = loadAndParseJson(path, commandInfo.bean.pluginDesc.classLoader) ?: return null
    commandSpecsCache.put(commandName, command)
    return command
  }

  private inline fun <reified T> loadAndParseJson(path: String, classLoader: ClassLoader): T? {
    val url = classLoader.getResource(path)
    if (url == null) {
      LOG.warn("Failed to find resource for path: $path with classLoader: $classLoader")
      return null
    }
    return try {
      val resultJson = url.readText()
      json.decodeFromString<T>(resultJson)
    }
    catch (ex: IOException) {
      LOG.warn("Failed to load resource by URL: $url", ex)
      null
    }
    catch (t: Throwable) {
      LOG.warn("Failed to parse resource loaded from URL: $url", t)
      null
    }
  }

  private data class ShellCommandInfo(val command: ShellCommand, val bean: CommandSpecsBean)

  companion object {
    @JvmStatic
    fun getInstance(): CommandSpecManager = service()

    private val LOG: Logger = logger<IJCommandSpecManager>()
  }
}
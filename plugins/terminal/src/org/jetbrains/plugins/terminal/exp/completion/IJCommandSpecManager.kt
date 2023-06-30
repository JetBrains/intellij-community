// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import java.io.IOException

class IJCommandSpecManager : CommandSpecManager {
  private val completionSpecs: MutableMap<String, ShellSubcommand> = HashMap()

  private val json: Json = Json {
    ignoreUnknownKeys = true
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
  override fun getCommandSpec(commandName: String): ShellSubcommand? {
    completionSpecs[commandName]?.let { return it }

    val (spec, path) = if (commandName.contains('/')) {
      val mainCommand = commandName.substringBefore('/')
      val mainSpec = findSpec(mainCommand) ?: return null
      val basePath = if (mainSpec.path.contains('/')) mainSpec.path.substringBeforeLast('/') else ""
      val specPath = "$basePath/$commandName.json"
      mainSpec to specPath
    }
    else {
      val spec = findSpec(commandName) ?: return null
      spec to spec.path
    }

    val specUrl = spec.pluginDesc.classLoader.getResource(path)
    @Suppress("UrlHashCode")
    if (specUrl == null) {
      LOG.warn("Failed to find spec resource for command: $commandName")
      return null
    }

    val subcommand: ShellSubcommand? = try {
      val specJson = specUrl.readText()
      json.decodeFromString(specJson)
    }
    catch (ex: IOException) {
      LOG.warn("Failed to load spec by url: $specUrl", ex)
      null
    }
    catch (t: Throwable) {
      LOG.warn("Failed to parse spec by url: $specUrl", t)
      null
    }

    if (subcommand != null) {
      completionSpecs[commandName] = subcommand
    }

    return subcommand
  }

  private fun findSpec(commandName: String): CommandSpecBean? {
    return CommandSpecBean.EP_NAME.extensionList.find { it.command == commandName }
  }

  companion object {
    @JvmStatic
    fun getInstance(): CommandSpecManager = service()

    private val LOG: Logger = logger<IJCommandSpecManager>()
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json

@Service
class CommandSpecManager {
  private val completionSpecs: MutableMap<String, ShellSubcommand> = HashMap()

  private val json: Json = Json {
    ignoreUnknownKeys = true
  }

  fun getCommandSpec(commandName: String): ShellSubcommand? {
    completionSpecs[commandName]?.let { return it }

    val spec = CommandSpecBean.EP_NAME.extensionList.find { it.command == commandName } ?: return null
    val url = this::class.java.classLoader.getResource(spec.path) ?: run {
      LOG.warn("Failed to find resource of completion spec: $spec")
      return null
    }

    val specJson = url.readText()
    val subcommand: ShellSubcommand? = try {
      json.decodeFromString(specJson)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to parse completion spec: $spec", t)
      null
    }

    if (subcommand != null) {
      completionSpecs[spec.command] = subcommand
    }

    return subcommand
  }

  companion object {
    @JvmStatic
    fun getInstance(): CommandSpecManager = service()

    private val LOG: Logger = logger<CommandSpecManager>()
  }
}
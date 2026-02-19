// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.sampullara.cli.Args


/**
 * Command verify module JDK version. Provides modes: CONTAINS, EQUALS.
 * Example: %assertModuleJdkVersionCommand module name|version 17|CONTAINS - verify that jdk versions for module [module name] contains [version 17]
 * Example: %assertModuleJdkVersionCommand module name|Amazon Corretto version 17.0.10|EQUALS - verify that jdk versions for module [module name] equals [Amazon Corretto version 17.0.10]
 */
class AssertModuleJdkVersionCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "assertModuleJdkVersionCommand"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val options = AssertModuleJdkArguments()

    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split("=") }.toTypedArray(), false)
    val moduleManager = ModuleManager.getInstance(project)
    val module = moduleManager.findModuleByName(options.moduleName)

    if (module == null) {
      throw IllegalArgumentException("No module with name: ${options.moduleName}")
    }

    val actualJdk = ModuleRootManager.getInstance(module).sdk
    if (actualJdk == null) {
      throw IllegalArgumentException("Actual jdk is null")
    }
    when (options.mode) {
      Mode.CONTAINS -> {
        if (actualJdk.versionString?.contains(options.jdkVersion) == false) {
          throw IllegalStateException("${actualJdk.versionString} does not contain ${options.jdkVersion}")
        }
      }
      Mode.EQUALS -> {
        if (actualJdk.versionString != options.jdkVersion) {
          throw IllegalStateException("Expected jdk version ${options.jdkVersion}, but got ${actualJdk.versionString}")
        }
      }
      else -> throw IllegalArgumentException("Mode should be CONTAINS or EQUALS")
    }


  }

  override fun getName(): String {
    return NAME
  }

  enum class Mode {
    CONTAINS,
    EQUALS
  }
}
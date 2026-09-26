// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.playback.PlaybackContext
import com.sampullara.cli.Args

/**
 * The command sets a jdk for a module
 * Syntax: %setModuleJdk [jdk_name] [jdk_type] [jdk_path] moduleName [module_name]
 * Example: %setModuleJdk java11 moduleName Main Module
 */
class SetModuleJdkCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "setModuleJdk"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val options = SetModuleJdkArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split("=") }.toTypedArray(), false)

    val moduleManager = ModuleManager.getInstance(project)
    val module = moduleManager.findModuleByName(options.moduleName)
    if (module == null) {
      throw IllegalArgumentException("No module with name: ${options.moduleName}")
    }

    val jdk = SetupProjectSdkUtil.setupOrDetectSdk(options.jdkName, options.jdkType, options.jdkPath)
    if (jdk == null) {
      throw IllegalArgumentException("No jdk with name: ${options.jdkName}")
    }
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.setSdk(jdk)
    }
    edtWriteAction { moduleManager.getModifiableModel().commit() }
  }

  override fun getName(): String {
    return NAME
  }
}
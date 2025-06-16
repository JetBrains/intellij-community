// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.ui.playback.PlaybackContext

class RenameModuleCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "renameModule"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (oldName, newName) = extractCommandList(PREFIX, " ")
    val moduleManager = getInstance(project)
    val modifiableModel = moduleManager.getModifiableModel()
    val module = moduleManager.modules.firstOrNull { it.name == oldName }
    if (module == null) {
      throw IllegalArgumentException("No module with name: $oldName")
    }
    modifiableModel.renameModule(module, newName)
    edtWriteAction { modifiableModel.commit() }
  }

  override fun getName(): String {
    return NAME
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAndWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.annotations.NonNls
import java.io.File

/**
 * Simplified version of [com.intellij.workspaceModel.performanceTesting.command.addModule.AddModulesContentCommand].
 */
class AddContentRootToModule(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "addContentRootToModule"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (moduleName, contentPath) = extractCommandArgument(PREFIX).split(",")

    constrainedReadAndWriteAction(ReadConstraint.inSmartMode(context.project)) {
      val moduleManager = ModuleManager.getInstance(context.project)
      val module = moduleManager.modules.firstOrNull { it.name == moduleName }
      if (module == null) {
        throw IllegalArgumentException("Cannot find module $moduleName. Available modules: ${moduleManager.modules.joinToString { it.name }}")
      }
      else {
        writeAction {
          ModuleRootModificationUtil.updateModel(module) { model ->
            val file = VfsUtil.findFileByIoFile(File(contentPath), true)
            if (file == null) {
              throw IllegalArgumentException("Cannot find file $contentPath")
            }
            model.addContentEntry(file).addSourceFolder(file, false)
          }
        }
      }
    }
  }
}
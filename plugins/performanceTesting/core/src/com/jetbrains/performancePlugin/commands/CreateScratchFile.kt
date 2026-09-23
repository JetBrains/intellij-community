// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileCreator
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.Language
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter


/**
 * Command creates text scratch file with content.
 * Example: %createScratchFile filename.txt "this is file content"
 *
 * For file content use "_" instead " " and "\\n" instead "\n"
 */
internal class CreateScratchFile(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = AbstractCommand.CMD_PREFIX + "createScratchFile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    extractCommandArgument(PREFIX).split(' ').filter { it.isNotEmpty() }.also {
      if (it.size != 2) {
        context.error("Unexpected number of arguments. Should be 2 args", line)
        return
      }
      val fileName = it.first()
      val fileContent = it.last().replace("\\n", "\n").replace("_", " ")
      // the Scratches root comes from the scratch plugin, which the frontend does not bundle
      val rootType = ScratchUtil.findScratchesRootType()
      if (rootType == null) {
        context.error("Scratch files are not available in this IDE", line)
        return
      }
      deleteFileIfExist(context, rootType, fileName)
      ScratchFileCreator.createScratchFile(
        context.project,
        fileName,
        Language.findLanguageByID("TEXT"),
        fileContent,
        ScratchFileService.Option.create_new_always,
        rootType
      )
    }
  }

  private suspend fun deleteFileIfExist(context: PlaybackContext, rootType: RootType, fileName: String) {
    val prevFile = readAction { rootType.findFile(context.project, fileName, ScratchFileService.Option.existing_only) }
    if (prevFile != null && prevFile.exists()) {
      edtWriteAction {
        prevFile.delete(this)
      }
    }
  }
}
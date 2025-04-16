// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.io.File
import kotlin.io.path.div

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
class StoreHighlightingResultsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {


  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "storeHighlightingResults"
    private val LOG: Logger
      get() = logger<StoreHighlightingResultsCommand>()
  }

  @Suppress("TestOnlyProblems")
  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val fileNameToStoreHighlightingInfo = extractCommandArgument(PREFIX).split(" ", limit = 1)[0]
    val fileForStoringHighlights: File = (PathManager.getLogDir() / "${fileNameToStoreHighlightingInfo}.txt").toFile()
    if (!fileForStoringHighlights.exists()) {
      fileForStoringHighlights.createNewFile()
    }

    LOG.info("Storing highlighting results to ${fileForStoringHighlights.path}, files exist: ${fileForStoringHighlights.exists()}")
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    require(editor != null)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        require(psiFile != null)

        var description: String
        DaemonCodeAnalyzerImpl.getHighlights(editor.document, null, project).forEach { highlight ->
          assert(editor.document.textLength > highlight.actualStartOffset) {
            "Highlight start offset ${editor.document.textLength} less than document text length ${highlight.actualStartOffset}"
          }
          description = if (highlight.description == null) "" else highlight.description.replace("\n", "")
          fileForStoringHighlights
            .appendText("${highlight.severity}☆$description☆${editor.document.getLineNumber(highlight.actualStartOffset)}☆${highlight.toolId}" + "\n")
        }
      }
    }
  }
}


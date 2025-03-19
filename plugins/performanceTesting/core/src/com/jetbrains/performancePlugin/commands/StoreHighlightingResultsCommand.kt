package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.io.File
import kotlin.io.path.div

class StoreHighlightingResultsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {


  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "storeHighlightingResults"
  }

  @Suppress("TestOnlyProblems")
  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val fileNameToStoreHighlightingInfo = extractCommandArgument(PREFIX).split(" ", limit = 1)[0]
    val fileForStoringHighlights: File = (PathManager.getLogDir() / "${fileNameToStoreHighlightingInfo}.txt").toFile()
    if (!fileForStoringHighlights.exists()) {
      fileForStoringHighlights.createNewFile()
    }

    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    require(editor != null)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        require(psiFile != null)
        for (severity in SeverityRegistrar.getSeverityRegistrar(project).allSeverities) {
          val highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.document, severity, project)
          highlights.forEach { highlight ->
            fileForStoringHighlights.appendText("${severity}☆${highlight.description}☆${editor.document.getLineNumber(highlight.actualStartOffset)}" + "\n")
          }
        }
      }
    }
  }
}


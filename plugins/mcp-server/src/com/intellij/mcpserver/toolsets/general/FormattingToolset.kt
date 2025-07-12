@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

class FormattingToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Reformats a specified file in the JetBrains IDE.
        |Use this tool to apply code formatting rules to a file identified by its path.
  """)
  suspend fun reformat_file(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    path: String,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.formatting.file", path))
    val project = currentCoroutineContext().project
    val resolvedFilePath = project.resolveInProject(path)

    val file = readAction {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedFilePath)
    } ?: mcpFail("File $resolvedFilePath doesn't exist or can't be opened")

    val psiFile = readAction {
      PsiManager.getInstance(project).findFile(file)
    } ?: mcpFail("File $file doesn't exist or can't be opened")

    val finished = CompletableDeferred<Unit>()
    val codeProcessor = ReformatCodeProcessor(psiFile, false)
    codeProcessor.setPostRunnable {
      finished.complete(Unit)
    }
    withContext(Dispatchers.EDT) {
      codeProcessor.run()
    }
    finished.await()
    return "ok"
  }
}
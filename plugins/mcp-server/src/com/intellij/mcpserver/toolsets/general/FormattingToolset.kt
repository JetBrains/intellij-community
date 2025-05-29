@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class FormattingToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Reformats the opened file in the JetBrains IDE editor.
        This tool doesn't require any parameters as it operates on the file currently open in the editor.
        
        Returns one of two possible responses:
            - "ok" if the file was successfully reformatted
            - "file doesn't exist or can't be opened" if there is no file currently selected in the editor
    """)
    suspend fun reformat_current_file(): String {
        val project = coroutineContext.project

        val psiFile = runReadAction {
            return@runReadAction FileEditorManager.getInstance(project).selectedTextEditor?.document?.run {
                PsiDocumentManager.getInstance(project).getPsiFile(this)
            }
        }

        if (psiFile == null) {
            return "file doesn't exist or can't be opened"
        }

        return suspendCancellableCoroutine { continuation ->
            val codeProcessor: ReformatCodeProcessor = ReformatCodeProcessor(psiFile, false)
            codeProcessor.setPostRunnable(Runnable {
                continuation.resume("ok")
            })
            ApplicationManager.getApplication().invokeLater(Runnable { codeProcessor.run() })
        }
    }

    @McpTool
    @McpDescription("""
        Reformats a specified file in the JetBrains IDE.
        Use this tool to apply code formatting rules to a file identified by its path.
        Requires a pathInProject parameter specifying the file location relative to the project root.
        
        Returns one of these responses:
        - "ok" if the file was successfully reformatted
        - error "project dir not found" if project directory cannot be determined
        - error "file doesn't exist or can't be opened" if the file doesn't exist or cannot be accessed
    """)
    suspend fun reformat_file(
        @McpDescription("Path to file relative to project root")
        pathInProject: String
    ): String {
        val project = coroutineContext.project

        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "project dir not found"

        val file = runReadAction {
            LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
        }

        if (file == null) {
            return "file doesn't exist or can't be opened"
        }

        val psiFile = runReadAction {
            return@runReadAction PsiManager.getInstance(project).findFile(file)
        }

        if (psiFile == null) {
            return "file doesn't exist or can't be opened"
        }

        return suspendCancellableCoroutine { continuation ->
            val codeProcessor: ReformatCodeProcessor = ReformatCodeProcessor(psiFile, false)
            codeProcessor.setPostRunnable(Runnable {
                continuation.resume("ok")
            })
            ApplicationManager.getApplication().invokeLater(Runnable { codeProcessor.run() })
        }
    }
}
package org.jetbrains.mcpserverplugin.general

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import org.jetbrains.mcpserverplugin.JsonUtils
import org.jetbrains.mcpserverplugin.general.relativizeByProjectDir
import org.jetbrains.mcpserverplugin.general.resolveRel


class GetCurrentFileErrorsTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_current_file_errors"
    override val description: String = """
        Analyzes the currently open file in the editor for errors and warnings using IntelliJ's inspections.
        Use this tool to identify coding issues, syntax errors, and other problems in your current file.
        Returns a JSON array of objects containing error information:
        - severity: The severity level of the issue (ERROR, WARNING, etc.)
        - description: A description of the issue
        - lineContent: The content of the line containing the issue
        Returns an empty array ([]) if no issues are found.
        Returns error if no file is currently open.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        return runReadAction {
            try {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor
                    ?: return@runReadAction Response(error = "no file open")

                val document = editor.document
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                    ?: return@runReadAction Response(error = "could not get PSI file")

                val projectDir = project.guessProjectDir()?.toNioPathOrNull()
                val filePath = psiFile.virtualFile?.toNioPathOrNull()?.relativizeByProjectDir(projectDir) ?: return@runReadAction Response(error = "could not get file path")

                val highlightInfos = getHighlightInfos(project, psiFile, document)
                val errorInfos = formatHighlightInfos(document, highlightInfos, filePath)

                Response(errorInfos.joinToString(",\n", prefix = "[", postfix = "]"))
            } catch (e: Exception) {
                Response(error = "Error analyzing file: ${e.message}")
            }
        }
    }

    private fun getHighlightInfos(project: Project, psiFile: PsiFile, document: Document): List<HighlightInfo> {
        val highlightInfos = mutableListOf<HighlightInfo>()
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.WEAK_WARNING, 0, document.textLength) { highlightInfo ->
            highlightInfos.add(highlightInfo)
            true
        }
        return highlightInfos
    }

    private fun formatHighlightInfos(document: Document, highlightInfos: List<HighlightInfo>, filePath: String): List<String> {
        return highlightInfos.map { info ->
            val startLine = document.getLineNumber(info.startOffset)
            val lineStartOffset = document.getLineStartOffset(startLine)
            val lineEndOffset = document.getLineEndOffset(startLine)
            val lineContent = document.getText(TextRange(lineStartOffset, lineEndOffset))
            """
            {
                "filePath": "${filePath}",
                "severity": "${info.severity}",
                "description": "${JsonUtils.escapeJson(info.description)}",
                "lineContent": "${JsonUtils.escapeJson(lineContent)}",
            }
            """.trimIndent()
        }
    }
}

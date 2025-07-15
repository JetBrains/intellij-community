package com.intellij.mcpserver.toolsets.general

import com.intellij.lang.documentation.impl.documentationTargets
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.SymbolInfo
import com.intellij.mcpserver.util.convertHtmlToMarkdown
import com.intellij.mcpserver.util.getElementSymbolInfo
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.documentation.impl.computeDocumentationAsync
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

class CodeInsightToolset : McpToolset {
  @McpTool
  @McpDescription("""
    |Retrieves information about the symbol at the specified position in the specified file.
    |Provides the same information as Quick Documentation feature of IntelliJ IDEA does.
    |
    |This tool is useful for getting information about the symbol at the specified position in the specified file.
    |The information may include the symbol's name, signature, type, documentation, etc. It depends on a particular language.
    |
    |If the position has a reference to a symbol the tool will return a piece of code with the declaration of the symbol if possible.
    |
    |Use this tool to understand symbols declaration, semantics, where it's declared, etc.
  """)
  suspend fun get_symbol_info(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    filePath: String,
    @McpDescription("1-based line number")
    line: Int,
    @McpDescription("1-based column number")
    column: Int,
  ): SymbolInfoResult {
    val project = currentCoroutineContext().project

    val resolvedPath = project.resolveInProject(filePath)
    val virtualFile = (LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
                       ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath))
                      ?: mcpFail("File not found: $filePath")
    val (documentationTargets, symbolInfo) = readAction {
      val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                     ?: mcpFail("Cannot read file: $filePath")
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                    ?: mcpFail("Cannot get symbol information for file '$filePath'")
      if (!DocumentUtil.isValidLine(line - 1, document)) mcpFail("Line number is out of bounds of the document")
      val lineStartOffset = document.getLineStartOffset(line - 1)
      val offset = lineStartOffset + column - 1
      if (!DocumentUtil.isValidOffset(offset, document)) mcpFail("Line and column $line:$column(offset=$offset) is out of bounds (file has ${document.textLength} characters)")
      val psiReference = psiFile.findReferenceAt(offset)
      val resolvedReference = psiReference?.resolve()

      documentationTargets(psiFile, offset).map { it.createPointer() } to resolvedReference?.let { getElementSymbolInfo(it, extraLines = 1) }
    }

    val results = coroutineScope {
      documentationTargets.map { pointer -> computeDocumentationAsync(pointer) }.awaitAll().filterNotNull()
    }

    val markdowns = results.joinToString("\n") { convertHtmlToMarkdown(it.html) }
    return SymbolInfoResult(
      symbolInfo = symbolInfo,
      documentation = markdowns
    )
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  data class SymbolInfoResult(
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val symbolInfo: SymbolInfo? = null,
    val documentation: String
  )
}
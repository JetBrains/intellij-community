package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlin.coroutines.coroutineContext

class CodeInsightToolset : McpToolset {

  @McpTool
  @McpDescription("""
        Finds all usages of a symbol in the specified file.
        Use this tool to locate where a symbol (variable, function, class, etc.) is used throughout the project.
        Requires two parameters:
            - pathInProject parameter specifying the file location from project root.
            - symbolName: The name of the symbol to find usages of
        Returns a JSON array of usage locations, where each location contains:
            - file: Path to the file containing the usage (relative to project root)
            - line: Line number where the usage is found (1-based)
            - column: Column number where the usage starts (1-based)
            - text: The line of text containing the usage
        Returns an empty array ([]) if no usages are found.
        Returns error message if the file or symbol cannot be found.
    """)
  suspend fun find_usages(
    @McpDescription("File location from project root.")
    pathInProject: String,
    @McpDescription("Name of the symbol to find usages of")
    symbolName: String,
  ): String {
    val project = coroutineContext.project
    val projectDir = project.guessProjectDir()?.toNioPathOrNull()
                     ?: return "can't find project dir"

    return runReadAction {
      try {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(pathInProject)
                          ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
                          ?: return@runReadAction "file not found: $pathInProject"

        val fileDocumentManager = FileDocumentManager.getInstance()
        val document = fileDocumentManager.getDocument(virtualFile)
                       ?: return@runReadAction "document not found: $pathInProject"
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val psiFile = psiDocumentManager.getPsiFile(document)
                      ?: return@runReadAction "couldn't get PSI file for: $pathInProject"

        val targetElement = findSymbolInFile(psiFile, symbolName)
                            ?: return@runReadAction "symbol not found: $symbolName in $pathInProject"

        val searchScope = GlobalSearchScope.allScope(project)
        val references = ReferencesSearch.search(targetElement, searchScope).findAll()

        val results = references.mapNotNull { reference ->
          val element = reference.element
          val file = element.containingFile?.virtualFile ?: return@mapNotNull null
          val documentFile = fileDocumentManager.getDocument(file) ?: return@mapNotNull null
          val psiFile = psiDocumentManager.getPsiFile(documentFile) ?: return@mapNotNull null

          val relativePath = try {
            projectDir.relativize(file.toNioPath()).toString()
          }
          catch (e: Exception) {
            file.path
          }

          val document = psiDocumentManager.getDocument(psiFile) ?: return@mapNotNull null
          val offset = reference.absoluteRange.startOffset

          val lineNumber = document.getLineNumber(offset) + 1

          val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
          val columnNumber = offset - lineStartOffset + 1

          val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
          val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset)).trim()

          """{"file": "$relativePath", "line": $lineNumber, "column": $columnNumber, "text": "$lineText"}"""
        }

        results.joinToString(",\n", prefix = "[", postfix = "]")

      }
      catch (e: Exception) {
        "Error finding usages: ${e.message}"
      }
    }
  }

  private fun findSymbolInFile(psiFile: PsiFile, symbolName: String): PsiElement? {
    val visitor = object : PsiRecursiveElementVisitor() {
      var foundElement: PsiElement? = null

      override fun visitElement(element: PsiElement) {
        if (foundElement != null) return

        if (element is PsiNamedElement) {
          if (element.name == symbolName) {
            foundElement = element
            return
          }
        }
        super.visitElement(element)
      }
    }

    psiFile.accept(visitor)
    return visitor.foundElement
  }
}
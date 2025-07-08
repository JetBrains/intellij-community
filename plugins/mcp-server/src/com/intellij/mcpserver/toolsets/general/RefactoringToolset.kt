package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class RefactoringToolset : McpToolset {
  @McpTool
  @McpDescription("""
        Renames a symbol (variable, function, class, etc.) in the specified file.
        Use this tool to perform rename refactoring operations. 
        
        The `rename_refactoring` tool is a powerful, context-aware utility. Unlike a simple text search-and-replace, 
        it understands the code's structure and will intelligently update ALL references to the specified symbol throughout the project,
        ensuring code integrity and preventing broken references. It is ALWAYS the preferred method for renaming programmatic symbols.

        Requires three parameters:
            - pathInProject: The relative path to the file from the project's root directory (e.g., `src/api/controllers/userController.js`)
            - symbolName: The exact, case-sensitive name of the existing symbol to be renamed (e.g., `getUserData`)
            - newName: The new, case-sensitive name for the symbol (e.g., `fetchUserData`).
            
        Returns a success message if the rename operation was successful.
        Returns an error message if the file or symbol cannot be found or the rename operation failed.
    """)
  suspend fun rename_refactoring(
    @McpDescription("File location from project root")
    pathInProject: String,
    @McpDescription("Name of the symbol to rename")
    symbolName: String,
    @McpDescription("New name for the symbol")
    newName: String,
  ): String {
    val project = coroutineContext.project
    val projectDir = project.guessProjectDir()?.toNioPathOrNull()
                     ?: return "can't find project dir"

    try {
      val virtualFile: VirtualFile? = readAction {
        LocalFileSystem.getInstance().findFileByPath(pathInProject)
        ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
      }

      if (virtualFile == null) {
        return "file not found: $pathInProject"
      }

      val fileDocumentManager = FileDocumentManager.getInstance()
      val document = readAction {
        fileDocumentManager.getDocument(virtualFile)
      }
      if (document == null) {
        return "document not found: $pathInProject"
      }
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      val psiFile = readAction {
        psiDocumentManager.getPsiFile(document)
      }
      if (psiFile == null) {
        return "couldn't get PSI file for: $pathInProject"
      }

      val targetElement = readAction {
        findSymbolInFile(psiFile, symbolName)
      }
      if (targetElement == null) {
        return "symbol not found: $symbolName in $pathInProject"
      }

      if (targetElement !is PsiNamedElement) {
        return "element is not renamable: $symbolName"
      }

      val renameProcessor = RenameProcessor(project, targetElement, newName, true, true)

      val usages = readAction { renameProcessor.findUsages() }

      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          renameProcessor.run()
        }
      }

      return "Successfully renamed '$symbolName' to '$newName' in $pathInProject with ${usages.size} usages."
    }
    catch (e: Exception) {
      return "Error during rename refactoring: ${e.message}"
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

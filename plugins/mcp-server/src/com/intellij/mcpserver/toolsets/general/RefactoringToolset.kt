package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

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
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    pathInProject: String,
    @McpDescription("Name of the symbol to rename")
    symbolName: String,
    @McpDescription("New name for the symbol")
    newName: String,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.renaming.symbol", symbolName, newName, pathInProject))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)

    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
                      ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                      ?: mcpFail("File not found: $pathInProject")

    val document = readAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)
    } ?: mcpFail("Cannot read file: $pathInProject")

    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val psiFile = readAction {
      psiDocumentManager.getPsiFile(document)
    } ?: mcpFail("couldn't get PSI file for: $pathInProject")

    val targetElement = readAction {
      findSymbolInFile(psiFile, symbolName)
    } ?: mcpFail("Couldn't find symbol '$symbolName' in file '$pathInProject'")

    if (targetElement !is PsiNamedElement) {
      mcpFail("Element is not renamable: $symbolName")
    }

    val renameProcessor = readAction { McpRenameProcessor(project, targetElement, newName, true, true) }

    val usages = readAction { renameProcessor.findUsages() }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        renameProcessor.run()
      }
    }
    return "Successfully renamed '$symbolName' to '$newName' in $pathInProject with ${usages.size} usages."
  }
}

/**
 * https://youtrack.jetbrains.com/issue/IJPL-196163
 */
private class McpRenameProcessor(
  project: Project,
  val element: PsiElement,
  val newName: String,
  isSearchInComments: Boolean,
  isSearchTextOccurrences: Boolean,
) : RenameProcessor(project,
                    element,
                    newName,
                    isSearchInComments,
                    isSearchTextOccurrences) {
  override fun isPreviewUsages(usages: Array<out UsageInfo?>): Boolean {
    return false
  }

  override fun showAutomaticRenamingDialog(automaticVariableRenamer: AutomaticRenamer?) = false

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo?>?>): Boolean {
    val usagesIn: Array<UsageInfo?> = refUsages.get() ?: return false
    val conflicts = MultiMap<PsiElement?, String?>()

    RenameUtil.addConflictDescriptions(usagesIn, conflicts)
    RenamePsiElementProcessor.forElement(element).findExistingNameConflicts(
      element, newName, conflicts, myAllRenames
    )
    if (!conflicts.isEmpty) {
      throw ConflictsFoundException()
    }
    return true
  }
}

private class ConflictsFoundException() : Exception() {
  override val message: String = "Conflicts were found during renaming"
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

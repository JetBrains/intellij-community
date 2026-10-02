package com.intellij.python.ruff

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.SuspendableImportOptimizer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiFile
import com.intellij.python.pytools.backend.isEnabledOn
import com.jetbrains.python.Result

private val LOG = logger<RuffImportOptimizer>()

/**
 * Import optimizer for a Python file, backed by the Ruff executable.
 *
 * Unlike the LSP `source.organizeImports` code action, which only sorts and regroups imports, this runs
 * `ruff check --fix-only` with the import-sorting (`I`) and unused-import (`F401`) rules force-selected. So
 * `Optimize Imports` both sorts imports and removes the unused ones, whatever the project's rule set enables.
 *
 * The work happens in [processFileSuspend], never in `processFile`. The platform calls `processFile` under a read
 * action, and from the Reformat Files dialog it calls it on the EDT. A process spawn there blocks every write action.
 */
class RuffImportOptimizer : SuspendableImportOptimizer {
  override fun supports(psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile ?: return false
    // An injected fragment or a non-local file has no path that Ruff can read its configuration from.
    if (!virtualFile.isInLocalFileSystem || virtualFile is VirtualFileWindow) return false

    val project = psiFile.project
    if (project.isDefault) return false
    // The LSP descriptor used to decide this. The fix now runs through the Ruff executable, so the server does not
    // have to be up, but the file must still belong to the project Ruff is configured for.
    if (!ProjectFileIndex.getInstance(project).isInContent(virtualFile)) return false
    if (!RuffPyTool.getInstance().isEnabledOn(project)) return false

    return project.service<RuffConfiguration>().sortImports
  }

  override suspend fun processFileSuspend(file: PsiFile): Runnable {
    val noResult = Runnable { }
    val virtualFile = file.virtualFile ?: return noResult
    // Remove this guard after PY-85408.
    if (virtualFile.extension.equals("ipynb", ignoreCase = true)) return noResult
    val path = virtualFile.ruffPath() ?: return noResult

    val snapshot = readAction {
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@readAction null
      document to document.text
    } ?: return noResult
    val (document, originalText) = snapshot

    // `ruff check --fix-only` applies the fixes and writes the resulting source to stdout, exiting 0 even when
    // unfixable violations remain. `I` sorts imports and `F401` removes the unused ones.
    val command = ruffStdinCommand(path, ruffStdinFallback(virtualFile, file.project), "check", "--fix-only", "--select", "I,F401")
    val scope = ruffScopeOf(file.project, virtualFile)
    val optimizedText = when (val result = RuffPyTool.getInstance().runOnStdin(scope, command, originalText)) {
      is Result.Success -> result.result
      is Result.Failure -> {
        LOG.warn("Ruff import optimization failed for $path: ${result.error.message}")
        return noResult
      }
    }
    if (optimizedText == originalText) return noResult

    return Runnable {
      // Skip a document that changed while Ruff was running: the result comes from text that is gone.
      if (document.text == originalText) {
        document.setText(optimizedText)
      }
    }
  }
}

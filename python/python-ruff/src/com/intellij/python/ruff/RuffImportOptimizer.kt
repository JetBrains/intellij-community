package com.intellij.python.ruff

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.getClients
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind.SourceOrganizeImports
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind

private val LOG = logger<RuffImportOptimizer>()

/**
 * Import optimizer that uses the Ruff LSP server to optimize imports in Python files.
 *
 */
class RuffImportOptimizer : ImportOptimizer {
  override fun supports(psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile ?: return false
    val project = psiFile.project

    val toolConfig = project.service<RuffConfiguration>()
    if (!toolConfig.sortImports) return false

    val lspServerManager = LspClientManager.getInstance(project)
    val servers = lspServerManager.getClients<RuffLspIntegrationProvider>()
    return servers.any { it.descriptor.isSupportedFile(virtualFile) }
  }

  override fun processFile(psiFile: PsiFile): Runnable {
    val noResult = Runnable { }
    val virtualFile = psiFile.virtualFile ?: return noResult
    val project = psiFile.project

    val lspServerManager = LspClientManager.getInstance(project)
    val servers = lspServerManager.getClients<RuffLspIntegrationProvider>()

    if (servers.isEmpty()) {
      LOG.warn("No Ruff LSP server found for file: ${virtualFile.path}")
      return noResult
    }

    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return noResult

    val server = servers.first { it.descriptor.isSupportedFile(virtualFile) }

    val range = getLsp4jRange(document, 0, document.textLength)

    val params = CodeActionParams(
      server.getDocumentIdentifier(virtualFile),
      range,
      CodeActionContext().apply {
        diagnostics = emptyList()
        triggerKind = CodeActionTriggerKind.Invoked
        only = listOf(SourceOrganizeImports)
      }
    )

    val tempDocument = DocumentImpl(document.text, false, true)

    val codeActions = server.sendRequestSync { it.textDocumentService.codeAction(params) } ?: return noResult

    var updated = false

    for (either in codeActions) {
      val codeAction = either.right ?: continue
      if (codeAction.data == null) continue

      val resolvedAction = server.sendRequestSync { it.textDocumentService.resolveCodeAction(codeAction) } ?: continue

      if (resolvedAction.edit == null) continue
      val changes = resolvedAction.edit?.documentChanges ?: continue
      for (change in changes) {
        if (!change.isLeft) continue
        val textDocumentEdit = change.left ?: continue
        val edits = textDocumentEdit.edits

        updated = true
        applyTextEdits(tempDocument, edits)
      }
    }
    return Runnable {
      if (updated) {
        document.setText(tempDocument.text)
      }
    }
  }
}

package com.intellij.python.ty.typeEngine

import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.psi.PsiFile
import com.intellij.python.lsp.core.type.LspTypeEvalContext
import com.intellij.python.lsp.core.type.PyStringTypeResolver
import com.intellij.python.ty.TyLsp4jServer
import com.intellij.python.ty.TyLspIntegrationProvider
import com.intellij.python.ty.TyUsageCollector
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.Unmodifiable


class TyTypeContext(psiFile: PsiFile) : LspTypeEvalContext(psiFile) {
  override fun requestTypes(
    pyTypedElements: @Unmodifiable Collection<PyTypedElement>,
  ): List<String?>? {
    val project = psiFile.project
    val lspServers = LspClientManager.getInstance(project)
      .getClients(TyLspIntegrationProvider::class.java as Class<out LspIntegrationProvider>)

    val lspServer = lspServers.firstOrNull() ?: return null

    val virtualFile = psiFile.virtualFile ?: return null
    val document = virtualFile.findDocument() ?: return null
    val args = mapOf(
      "textDocument" to lspServer.getDocumentIdentifier(virtualFile),
      "ranges" to pyTypedElements.map {
        val startOffset = it.textRange?.startOffset ?: return@map null
        getLsp4jRange(document, startOffset, it.textLength)
      },
    )

    val response = lspServer.sendRequestSync {
      (it as TyLsp4jServer).provideType(args)
    }

    @Suppress("UNCHECKED_CAST")
    val tys = (response?.get("tys") as? List<String>) ?: (response?.get("types") as? List<String>) ?: return null
    return tys
  }

  override fun resolveStringType(element: PyTypedElement, stringType: String): Ref<PyType?>? {
    return TyUsageCollector.logStringTypeResolutionTime {
      PyStringTypeResolver.resolvePyType(element, stringType)
    }
  }
}
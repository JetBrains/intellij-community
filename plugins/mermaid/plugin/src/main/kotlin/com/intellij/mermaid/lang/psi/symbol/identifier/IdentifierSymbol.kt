package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationService
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange

@Suppress("UnstableApiUsage")
class IdentifierSymbol(
  file: PsiFile,
  range: TextRange,
  text: String
): UnresolvedIdentifierSymbol(file, range, text), NavigationTarget {
  override fun createPointer(): Pointer<out IdentifierSymbol> {
    val project = file.project
    val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range)
    return IdentifierSymbolPointer(base, text)
  }

  private class IdentifierSymbolPointer(
    private val base: SmartPsiFileRange,
    private val text: String
  ) : Pointer<IdentifierSymbol> {
    override fun dereference(): IdentifierSymbol? {
      val file = base.containingFile ?: return null
      val range = base.range ?: return null
      return IdentifierSymbol(file, TextRange.create(range), text)
    }
  }

  override fun navigationRequest(): NavigationRequest? {
    val virtualFile = file.virtualFile?.takeIf { it.isValid } ?: return null
    val navigationService = service<NavigationService>()
    return navigationService.sourceNavigationRequest(virtualFile, range.startOffset)
  }

  companion object {
    fun createPointer(element: MermaidNamedPsiElement): Pointer<IdentifierSymbol> {
      val (base, textInElement) = getPointerArgs(element)
      return IdentifierSymbolPointer(base, textInElement)
    }
  }
}

package org.intellij.plugins.markdown.model.psi

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

internal class MarkdownPsiUsage(
  override val file: PsiFile,
  override val range: TextRange,
  override val declaration: Boolean
): PsiUsage {
  override fun createPointer(): Pointer<out PsiUsage> {
    return MarkdownPsiUsagePointer(file, range, declaration)
  }

  private class MarkdownPsiUsagePointer(
    file: PsiFile,
    range: TextRange,
    private val declaration: Boolean
  ): Pointer<MarkdownPsiUsage> {
    private val rangePointer = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

    override fun dereference(): MarkdownPsiUsage? {
      val file = rangePointer.element ?: return null
      val range = rangePointer.range?.let(TextRange::create) ?: return null
      return MarkdownPsiUsage(file, range, declaration)
    }
  }

  companion object {
    fun create(element: PsiElement, rangeInElement: TextRange, declaration: Boolean = false): MarkdownPsiUsage {
      if (element is PsiFile) {
        return MarkdownPsiUsage(element, rangeInElement, declaration)
      }
      return MarkdownPsiUsage(
        element.containingFile,
        rangeInElement.shiftRight(element.textRange.startOffset),
        declaration
      )
    }

    fun create(reference: PsiSymbolReference): MarkdownPsiUsage {
      return create(reference.element, reference.rangeInElement, declaration = false)
    }
  }
}

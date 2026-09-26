// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UnusedLinkDefinitionInspection: LocalInspectionTool(), DumbAware {
  override fun runForWholeFile(): Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val definitions = LinkDefinitions.get(holder.file)
    if (definitions.definitions.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val usableDefinitions = definitions.definitions.toHashSet()
    val usedDefinitionRanges = collectUsedDefinitionRanges(holder.file)
    return object: MarkdownElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is MarkdownLinkDefinition || element !in usableDefinitions) {
          return
        }
        val label = element.linkLabel
        if (definitions.find(label.labelText) != element) {
          return
        }
        val range = label.labelTextRange.shiftRight(label.textRange.startOffset)
        if (range !in usedDefinitionRanges) {
          holder.registerProblem(
            label,
            MarkdownBundle.message("markdown.unused.link.definition.inspection.name"),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            label.labelTextRange
          )
        }
      }
    }
  }

  private fun collectUsedDefinitionRanges(file: PsiFile): Set<TextRange> {
    val ranges = HashSet<TextRange>()
    for (element in SyntaxTraverser.psiTraverser(file).expand { it !is MarkdownLinkDefinition }) {
      ProgressManager.checkCanceled()
      if (element !is MarkdownLinkLabel) continue
      val references = MarkdownPsiSymbolReference.findSymbolReferences(element).filterIsInstance<LinkLabelSymbolReference>()
      for (reference in references) {
        reference.resolveReference().filterIsInstance<LinkLabelSymbol>().mapTo(ranges) { it.range }
      }
    }
    return ranges
  }
}

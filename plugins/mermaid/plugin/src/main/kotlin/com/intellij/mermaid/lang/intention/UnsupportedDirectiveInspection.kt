package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.*
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.children
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet


class UnsupportedDirectiveInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
  private val newDiagrams = TokenSet.create(
    MermaidTokens.ZenUML.ZEN_UML,
    MermaidTokens.Sankey.SANKEY
  )

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is MermaidFile) return null

    SyntaxTraverser.psiTraverser(file).firstOrNull { it.hasType(newDiagrams) } ?: return null

    val openDirective = file.children().firstOrNull { it.hasType(MermaidTokens.Directives.OPEN_DIRECTIVE) } ?: return null
    val closeDirective = file.children().firstOrNull { it.hasType(MermaidTokens.Directives.CLOSE_DIRECTIVE) } ?: return null

    return arrayOf(
      manager.createProblemDescriptor(
        openDirective,
        closeDirective,
        MermaidBundle.message("annotator.unsupported.directive"),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        isOnTheFly,
        RemoveQuickFix()
      )
    )
  }

  class RemoveQuickFix : LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.delete.directive")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      when (val element = descriptor.psiElement) {
        is LeafPsiElement -> element.delete()
        else -> element.deleteChildRange(descriptor.startElement, descriptor.endElement)
      }
    }
  }
}

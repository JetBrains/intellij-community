package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.*
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidJourneyDataStatement
import com.intellij.mermaid.lang.psi.MermaidVisitor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings

class UnusedAttributesInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (holder.file !is MermaidFile) return PsiElementVisitor.EMPTY_VISITOR
    return object : MermaidVisitor() {
      override fun visitJourneyDataStatement(o: MermaidJourneyDataStatement) {
        val taskDataList = o.journeyTaskDataList
        if (taskDataList.size >= 2) {
          val secondTaskData = taskDataList[1]

          val startElement = secondTaskData
            .siblings(withSelf = false)
            .filter { it.elementType == MermaidTokens.COLON }
            .firstOrNull() ?: return

          val endElement = o.lastChild

          holder.registerProblem(
            InspectionManagerBase.getInstance(holder.project)
              .createProblemDescriptor(
                startElement,
                endElement,
                MermaidBundle.message("unused.attribute.inspection.display.name"),
                ProblemHighlightType.WARNING,
                isOnTheFly,
                RemoveQuickFix()
              )
          )
        }
      }
    }
  }

  class RemoveQuickFix : LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.remove.unused.attributes")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      if (element is LeafPsiElement) {
        element.delete()
      } else {
        element.deleteChildRange(descriptor.startElement, descriptor.endElement)
      }
    }
  }
}

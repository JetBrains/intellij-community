// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.InspectionManagerBase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
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
      override fun visitJourneyDataStatement(mermaidJourneyDataStatement: MermaidJourneyDataStatement) {
        val taskDataList = mermaidJourneyDataStatement.sectionTaskDataList
        if (taskDataList.size >= 1) {
          val secondTaskData = mermaidJourneyDataStatement.journeyNamedData ?: return

          val startElement = secondTaskData
            .siblings(forward = true, withSelf = false)
            .filter { it.elementType == MermaidTokens.COLON }
            .firstOrNull() ?: return

          val endElement = mermaidJourneyDataStatement.lastChild

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
      when (val element = descriptor.psiElement) {
        is LeafPsiElement -> element.delete()
        else -> element.deleteChildRange(descriptor.startElement, descriptor.endElement)
      }
    }
  }
}

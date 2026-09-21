// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DuplicateLinkDefinitionInspection: LocalInspectionTool(), DumbAware {
  override fun runForWholeFile(): Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val definitions = LinkDefinitions.get(holder.file)
    if (definitions.definitions.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val usableDefinitions = definitions.definitions.toHashSet()
    return object: MarkdownElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is MarkdownLinkDefinition || element !in usableDefinitions) {
          return
        }
        val label = element.linkLabel
        if (definitions.find(label.labelText) != element) {
          holder.registerProblem(
            label,
            MarkdownBundle.message("markdown.duplicate.link.definition.inspection.name"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            label.labelTextRange
          )
        }
      }
    }
  }
}

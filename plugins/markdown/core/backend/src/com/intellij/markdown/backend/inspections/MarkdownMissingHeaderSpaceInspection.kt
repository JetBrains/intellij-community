// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.text.CharSequenceSubSequence
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownMissingHeaderSpaceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : MarkdownElementVisitor() {
      override fun visitParagraph(paragraph: MarkdownParagraph) {
        super.visitParagraph(paragraph)
        val start = paragraph.textRange.startOffset
        val text = CharSequenceSubSequence(holder.file.viewProvider.contents, start, start + paragraph.textLength)
        val match = MISSING_SPACE.matchAt(text, 0) ?: return
        holder.registerProblem(
          paragraph,
          TextRange(0, match.value.length),
          MarkdownBundle.message("markdown.missing.header.space.inspection.description"),
          InsertSpaceFix(match.groupValues[1].length)
        )
      }
    }
  }

  private class InsertSpaceFix(private val offset: Int) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = MarkdownBundle.message("markdown.missing.header.space.quick.fix.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      element.containingFile.fileDocument.insertString(element.textRange.startOffset + offset, " ")
    }
  }
}

private val MISSING_SPACE = Regex("""(#{1,6})[^#\s\d]\S*""")

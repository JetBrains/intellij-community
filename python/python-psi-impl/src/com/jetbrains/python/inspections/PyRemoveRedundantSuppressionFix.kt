// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.SuppressionUtil
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import java.util.regex.Pattern

private val SUPPRESS_PATTERN: Pattern = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP)
private val ID_SEPARATOR = Regex("[,\\s]+")

/**
 * Removes a single inspection id from a `# noinspection` comment, deleting the whole comment line
 * when that id is the only one being suppressed. Produced by [PyInspectionsSuppressor] to clear up
 * suppressions reported as redundant by the platform `RedundantSuppression` inspection.
 */
internal class PyRemoveRedundantSuppressionFix(private val toolId: String) : PsiUpdateModCommandQuickFix() {
  override fun getName(): String = PyPsiBundle.message("INSP.redundant.suppression.remove.quickfix.name", toolId)

  override fun getFamilyName(): String = PyPsiBundle.message("INSP.redundant.suppression.remove.quickfix.family.name")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val comment = element as? PsiComment ?: element.parentOfType<PsiComment>(withSelf = true) ?: return
    val matcher = SUPPRESS_PATTERN.matcher(comment.text.substring(1).trim())
    if (!matcher.matches()) return
    val remaining = matcher.group(1).split(ID_SEPARATOR).filter { it.isNotBlank() && it != toolId }
    if (remaining.isEmpty()) {
      comment.delete()
    }
    else {
      val newComment = PyElementGenerator.getInstance(project)
        .createFromText(LanguageLevel.forElement(comment), PsiComment::class.java, "# noinspection " + remaining.joinToString(", "))
      comment.replace(newComment)
    }
  }
}

package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyConvertToRawStringQuickFix(private val offsetInElement: Int) : PsiUpdateModCommandQuickFix(), LowPriorityAction {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.convert.to.raw.string")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    if (element !is PyStringLiteralExpression) return

    val part = element.stringElements.find { it.textRangeInParent.contains(offsetInElement) } ?: return
    if (part.prefix.contains("r", ignoreCase = true)) return

    val newText = "r" + part.text

    val newPart = PyElementGenerator.getInstance(project)
                    .createStringLiteralAlreadyEscaped(newText)
                    .stringElements.firstOrNull() ?: return
    part.replace(newPart)
  }
}

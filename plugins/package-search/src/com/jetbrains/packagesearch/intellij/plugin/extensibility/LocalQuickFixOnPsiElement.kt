package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

@Suppress("FunctionName")
internal fun LocalQuickFixOnPsiElement(
    element: PsiElement,
    @Nls familyName: String,
    @Nls text: String,
    isHighPriority: Boolean,
    action: Project.() -> Unit
): LocalQuickFix = if (isHighPriority) object : LocalQuickFixOnPsiElement(element), HighPriorityAction {
    override fun getFamilyName() = familyName
    override fun getText() = text
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        project.action()
} else object : LocalQuickFixOnPsiElement(element), LowPriorityAction {
    override fun getFamilyName() = familyName
    override fun getText() = text
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        project.action()
}
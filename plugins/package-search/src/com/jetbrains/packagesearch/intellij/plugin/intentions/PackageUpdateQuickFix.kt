package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ExecutablePackageOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationTarget

class PackageUpdateQuickFix(
    element: PsiElement,
    private val target: PackageOperationTarget,
    private val dependency: StandardV2Package,
    private val version: String
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.update.family")

    override fun getText() = PackageSearchBundle.message("packagesearch.quickfix.update.action", dependency.toSimpleIdentifier(), version)

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val model = project.getUserData(PackageSearchToolWindowFactory.ToolWindowModelKey) ?: return
        val operation = target.getApplyOperation(version) ?: return

        model.executeOperations(listOf(ExecutablePackageOperation(operation, target, version)))
    }
}

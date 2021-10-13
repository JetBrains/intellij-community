package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.NotifyingOperationExecutor

internal class PackageSearchDependencyUpgradeQuickFix(
    element: PsiElement,
    private val uiPackageModel: UiPackageModel.Installed
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family")

    override fun getText() = PackageSearchBundle.message(
        "packagesearch.quickfix.upgrade.action",
        uiPackageModel.identifier.rawValue,
        uiPackageModel.selectedVersion
    )

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val operations = uiPackageModel.packageOperations.primaryOperations

        NotifyingOperationExecutor(project).executeOperations(operations)
    }
}

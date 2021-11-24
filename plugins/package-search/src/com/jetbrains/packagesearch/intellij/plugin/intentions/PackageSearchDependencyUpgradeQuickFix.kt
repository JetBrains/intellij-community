package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.NotifyingOperationExecutor

internal class PackageSearchDependencyUpgradeQuickFix(
    element: PsiElement,
    private val identifier: PackageIdentifier,
    private val targetVersion: PackageVersion.Named,
    private val operations: List<PackageSearchOperation<*>>
) : LocalQuickFixAndIntentionActionOnPsiElement(element), HighPriorityAction {

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family")

    override fun getText() = PackageSearchBundle.message(
        "packagesearch.quickfix.upgrade.action",
        identifier.rawValue,
        targetVersion
    )

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        NotifyingOperationExecutor(project).executeOperations(operations)
    }
}

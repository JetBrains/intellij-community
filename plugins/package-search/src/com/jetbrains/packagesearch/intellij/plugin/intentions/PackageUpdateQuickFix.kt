package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.util.dataService

internal class PackageUpdateQuickFix(
    element: PsiElement,
    private val dependency: UnifiedDependency,
    private val projectModule: ProjectModule,
    private val targetVersion: PackageVersion
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    private val operationFactory = PackageSearchOperationFactory()

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.update.family")

    override fun getText() = PackageSearchBundle.message("packagesearch.quickfix.update.action", dependency.coordinates.displayName, targetVersion)

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val rootModel = project.dataService()
        val operations = operationFactory.createChangePackageVersionOperations(
            projectModule = projectModule,
            dependency = dependency,
            newVersion = targetVersion,
            repoToInstall = null // TODO Figure out a way to also add required repositories if need be
        )

        rootModel.executeOperations(operations)
    }
}

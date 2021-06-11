package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService

internal class PackageSearchDependencyUpdateQuickFix(
    element: PsiElement,
    private val packageModel: PackageModel.Installed,
    private val unifiedDependency: UnifiedDependency,
    private val projectModule: ProjectModule,
    private val targetVersion: PackageVersion
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    private val operationFactory = PackageSearchOperationFactory()

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family")

    override fun getText() = PackageSearchBundle.message(
        "packagesearch.quickfix.upgrade.action",
        unifiedDependency.coordinates.copy(version = null).displayName,
        targetVersion
    )

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        checkNotNull(unifiedDependency.coordinates.version) { "The dependency ${unifiedDependency.coordinates.displayName} has no set version" }
        val dataModel = project.packageSearchDataService.dataModelFlow.value
        val selectedVersion = PackageVersion.from(unifiedDependency.coordinates.version)

        val repoToInstall = dataModel.knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
            packageModel,
            selectedVersion,
            dataModel.allKnownRepositories
        )
        val operations = operationFactory.createChangePackageVersionOperations(
            projectModule = projectModule,
            dependency = unifiedDependency,
            newVersion = targetVersion,
            repoToInstall = repoToInstall,
        )

        project.packageSearchDataService.executeOperations(operations)
        PackageSearchEventsLogger.logRunQuickFix(FUSGroupIds.QuickFixTypes.DependencyUpdate, file.fileType.name)
    }
}

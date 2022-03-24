package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.toUnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.util.toUnifiedRepository

internal class PackageSearchOperationFactory {

    inline fun <reified T : PackageModel, V : PackageVersion> computeInstallActionsFor(
        project: Project,
        packageModel: T,
        moduleModel: ModuleModel,
        defaultScope: PackageScope,
        knownRepositories: KnownRepositories.InTargetModules,
        targetVersion: NormalizedPackageVersion<V>
    ): List<PackageSearchOperation<*>> {
        if (packageModel !is PackageModel.SearchResult) return emptyList()

        val versionToInstall = targetVersion.originalVersion
        return createAddPackageOperations(
            packageModel = packageModel,
            version = versionToInstall,
            scope = defaultScope,
            targetModules = TargetModules.from(moduleModel),
            repoToInstall = knownRepositories.repositoryToAddWhenInstallingOrUpgrading(project, packageModel, versionToInstall)
        )
    }

    inline fun <reified T : PackageModel, V : PackageVersion> computeUpgradeActionsFor(
        project: Project,
        packageModel: T,
        moduleModel: ModuleModel,
        knownRepositories: KnownRepositories.InTargetModules,
        targetVersion: NormalizedPackageVersion<V>
    ): List<PackageSearchOperation<*>> {
        if (packageModel !is PackageModel.Installed) return emptyList()

        return packageModel.usageInfo.asSequence()
            .filter { it.projectModule == moduleModel.projectModule }
            .flatMap { usageInfo ->
                createChangePackageVersionOperations(
                    packageModel = packageModel,
                    newVersion = targetVersion.originalVersion,
                    targetModules = TargetModules.from(moduleModel),
                    repoToInstall = knownRepositories.repositoryToAddWhenInstallingOrUpgrading(project, packageModel, targetVersion.originalVersion)
                )
            }
            .toList()
    }

    inline fun <reified T : PackageModel> computeRemoveActionsFor(
        packageModel: T,
        moduleModel: ModuleModel
    ): List<PackageSearchOperation<*>> {
        if (packageModel !is PackageModel.Installed) return emptyList()

        return packageModel.usageInfo.asSequence()
            .filter { it.projectModule == moduleModel.projectModule }
            .flatMap { usageInfo ->
                createRemovePackageOperations(
                    packageModel = packageModel,
                    version = usageInfo.version,
                    scope = usageInfo.scope,
                    targetModules = TargetModules.from(moduleModel)
                )
            }
            .toList()
    }

    fun createChangePackageVersionOperations(
        packageModel: PackageModel.Installed,
        newVersion: PackageVersion,
        targetModules: TargetModules,
        repoToInstall: RepositoryModel?
    ) = createChangePackageOperations(
        packageModel = packageModel,
        newVersion = newVersion,
        newScope = PackageScope.Missing,
        targetModules = targetModules,
        repoToInstall = repoToInstall
    )

    fun createChangePackageScopeOperations(
        packageModel: PackageModel.Installed,
        newScope: PackageScope,
        targetModules: TargetModules,
        repoToInstall: RepositoryModel?
    ) = createChangePackageOperations(
        packageModel = packageModel,
        newVersion = PackageVersion.Missing,
        newScope = newScope,
        targetModules = targetModules,
        repoToInstall = repoToInstall
    )

    fun createChangePackageVersionOperations(
        projectModule: ProjectModule,
        dependency: UnifiedDependency,
        newVersion: PackageVersion,
        repoToInstall: RepositoryModel?
    ): List<PackageSearchOperation<*>> {
        val currentScope = PackageScope.from(dependency.scope)
        val currentVersion = PackageVersion.from(dependency.coordinates.version)

        val packageOperation = PackageSearchOperation.Package.ChangeInstalled(
            model = dependency,
            projectModule = projectModule,
            currentVersion = currentVersion,
            currentScope = currentScope,
            newVersion = newVersion,
            newScope = currentScope
        )

        return if (repoToInstall != null) {
            val repoToInstallOperation = createAddRepositoryOperation(repoToInstall, projectModule)
            listOf(packageOperation, repoToInstallOperation)
        } else {
            listOf(packageOperation)
        }
    }

    fun createChangePackageOperations(
        packageModel: PackageModel.Installed,
        newVersion: PackageVersion,
        newScope: PackageScope,
        targetModules: TargetModules,
        repoToInstall: RepositoryModel?
    ): List<PackageSearchOperation<*>> {
        if (targetModules is TargetModules.None) return emptyList()

        return usagesByModule(targetModules, packageModel)
            .flatMap { (module, usageInfo) ->
                val packageOperation = PackageSearchOperation.Package.ChangeInstalled(
                    model = packageModel.toUnifiedDependency(usageInfo.version, usageInfo.scope),
                    projectModule = module.projectModule,
                    currentVersion = usageInfo.version,
                    currentScope = usageInfo.scope,
                    newVersion = newVersion,
                    newScope = newScope
                )

                if (repoToInstall != null) {
                    val repoToInstallOperation = createAddRepositoryOperation(repoToInstall, module.projectModule)
                    listOf(packageOperation, repoToInstallOperation)
                } else {
                    listOf(packageOperation)
                }
            }
    }

    fun createAddPackageOperations(
        packageModel: PackageModel.SearchResult,
        version: PackageVersion,
        scope: PackageScope,
        targetModules: TargetModules,
        repoToInstall: RepositoryModel?
    ): List<PackageSearchOperation<*>> {
        if (targetModules is TargetModules.None) return emptyList()

        return targetModules.flatMap { module ->
            val packageOperation = PackageSearchOperation.Package.Install(
                model = packageModel.toUnifiedDependency(version, scope),
                projectModule = module.projectModule,
                newVersion = version,
                newScope = scope
            )

            if (repoToInstall != null) {
                val repoToInstallOperation = createAddRepositoryOperation(repoToInstall, module.projectModule)
                listOf(packageOperation, repoToInstallOperation)
            } else {
                listOf(packageOperation)
            }
        }
    }

    fun createRemovePackageOperations(
        packageModel: PackageModel.Installed,
        version: PackageVersion,
        scope: PackageScope,
        targetModules: TargetModules
    ): List<PackageSearchOperation<*>> {
        if (targetModules is TargetModules.None) return emptyList()

        return usagesByModule(targetModules, packageModel)
            .map { (module, usageInfo) ->
                PackageSearchOperation.Package.Remove(
                    model = packageModel.toUnifiedDependency(usageInfo.version, usageInfo.scope),
                    projectModule = module.projectModule,
                    currentVersion = version,
                    currentScope = scope
                )
            }
    }

    private fun usagesByModule(targetModules: TargetModules, packageModel: PackageModel.Installed) =
        targetModules.flatMap { module ->
            packageModel.usageInfo.filter { usageInfo -> usageInfo.projectModule == module.projectModule }
                .map { usageInfo -> module to usageInfo }
        }

    private fun createAddRepositoryOperation(
        repoToInstall: RepositoryModel,
        projectModule: ProjectModule
    ) = PackageSearchOperation.Repository.Install(
        model = repoToInstall.toUnifiedRepository(),
        projectModule = projectModule
    )
}
